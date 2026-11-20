import re,os,json,sys,collections
ROOT='app/src/main'
KT=[]
for d,_,fs in os.walk(ROOT+'/java'):
    for f in fs:
        if f.endswith('.kt'): KT.append(os.path.join(d,f))
src={p:open(p,encoding='utf-8').read() for p in KT}

def strip(s):
    # remove comments and string literals (rough) for brace counting / identifier scans
    s=re.sub(r'"""(.|\n)*?"""','""',s)
    s=re.sub(r'"(\\.|[^"\\\n])*"','""',s)
    s=re.sub(r"'(\\.|[^'\\\n])'","''",s)
    s=re.sub(r'/\*.*?\*/','',s,flags=re.S)
    s=re.sub(r'//[^\n]*','',s)
    return s
clean={p:strip(s) for p,s in src.items()}

pkg={}
decl=collections.defaultdict(lambda: collections.defaultdict(list))  # pkg -> name -> [(file,kind,line)]
DECL=re.compile(r'^(?:\s*)(?:(?:public|private|internal|protected|override|open|abstract|sealed|data|enum|inline|suspend|operator|tailrec|const|lateinit|@\w+(?:\([^)]*\))?)\s+)*(fun|val|var|class|object|interface|typealias)\s+(?:<[^>]*>\s*)?(?:[\w.]+\.)?(\w+)',re.M)
for p,s in clean.items():
    m=re.search(r'^package\s+([\w.]+)',s,re.M)
    pkg[p]=m.group(1) if m else ''
    # only top-level declarations: indentation 0
    for m in re.finditer(r'^((?:(?:public|private|internal|override|open|abstract|sealed|data|enum|inline|suspend|operator|const|lateinit)\s+)*)(fun|val|var|class|object|interface|typealias)\s+(?:<[^>]*>\s*)?(?:[\w.<>?,\s]+\.)?(\w+)',s,re.M):
        mods,kind,name=m.groups()
        if 'private' in mods: continue
        line=s[:m.start()].count('\n')+1
        decl[pkg[p]][name].append((os.path.relpath(p,ROOT),kind,line))
    # members of top-level objects are referenced as Obj.member, not imported

issues=[]
# 1. imports resolve
for p,s in src.items():
    for m in re.finditer(r'^import\s+([\w.]+)(?:\s+as\s+\w+)?\s*$',s,re.M):
        full=m.group(1)
        if not full.startswith('com.myapp.app'): continue
        parts=full.split('.')
        name=parts[-1]; pk='.'.join(parts[:-1])
        if name in decl.get(pk,{}): continue
        # maybe nested member import like Object.member
        issues.append(f'IMPORT? {os.path.relpath(p,ROOT)}: {full} not found as a top-level declaration of package {pk}')

# 2. duplicate top-level function/val/class names in one package
for pk,names in decl.items():
    for name,lst in names.items():
        funs=[x for x in lst if x[1]=='fun']
        others=[x for x in lst if x[1]!='fun']
        if len(others)>1 or (others and funs):
            issues.append(f'DUP {pk}.{name}: '+', '.join(f'{f}:{l}({k})' for f,k,l in lst))
        elif len(funs)>1:
            issues.append(f'DUP-FUN(check overloads) {pk}.{name}: '+', '.join(f'{f}:{l}' for f,k,l in funs))

# 3. brace balance
for p,s in clean.items():
    for a,b in ('{}','()','[]'):
        if s.count(a)!=s.count(b):
            issues.append(f'BALANCE {os.path.relpath(p,ROOT)}: {a} {s.count(a)} vs {b} {s.count(b)}')

# 4. used-but-not-imported project symbols (top-level fun/class/object from other packages)
byname=collections.defaultdict(set)
for pk,names in decl.items():
    for name,lst in names.items():
        if len(name)>=4:
            byname[name].add(pk)
for p,s in clean.items():
    mypk=pkg[p]
    imported=set(re.findall(r'^import\s+[\w.]+\.(\w+)(?:\s+as\s+\w+)?\s*$',src[p],re.M))
    aliases=set(re.findall(r'\bas\s+(\w+)',src[p]))
    for name,pks in byname.items():
        if mypk in pks or name in imported: continue
        # used as identifier call/ref not qualified
        for m in re.finditer(r'(?<![\w.])'+re.escape(name)+r'\b(?=\s*[(.<{:]|\s*$)',s,re.M):
            line=s[:m.start()].count('\n')+1
            ln=s.split('\n')[line-1]
            if re.match(r'\s*(import|package)\b',ln): continue
            # ignore parameter names / named args (name =) and local declarations
            if re.search(r'\b(val|var)\s+'+re.escape(name)+r'\b',ln): continue
            if re.search(r'\bfun\s+'+re.escape(name)+r'\b',ln): continue
            issues.append(f'NOIMPORT? {os.path.relpath(p,ROOT)}:{line}: {name} (declared in {", ".join(sorted(pks))})')
            break

# 5. i18n keys
bn=json.load(open(ROOT+'/assets/i18n_bn.json',encoding='utf-8'))
for p,s in src.items():
    for m in re.finditer(r'\btrf?(?:Map)?\(\s*"([A-Za-z0-9_]+)"',s):
        if m.group(1) not in bn:
            issues.append(f'I18N missing key {m.group(1)} in {os.path.relpath(p,ROOT)}')

# 6. FA icons used vs glyph map
gl=open(ROOT+'/java/com/myapp/app/ui/components/FaGlyphs.kt',encoding='utf-8').read()
glyphs=set(re.findall(r'"([a-z0-9-]+)" to 0x',gl))
special={'arrow-up-right','arrow-down-left'}
for p,s in src.items():
    if p.endswith('FaGlyphs.kt'): continue
    for m in re.finditer(r'"fa-([a-z0-9-]+)"',s):
        n=m.group(1)
        if n not in glyphs and n not in special:
            issues.append(f'ICON not in glyph map: fa-{n} in {os.path.relpath(p,ROOT)}')

# 7. Popups flags / AppActions members used exist
pl=src[ROOT+'/java/com/myapp/app/ui/popup/PopupLayer.kt']
flags=set(re.findall(r'var\s+(\w+)\s+by\s+mutableStateOf',pl))
for p,s in src.items():
    for m in re.finditer(r'\bPopups\.(\w+)',s):
        if m.group(1) not in flags|{'anyVisible','alert','confirm'}:
            issues.append(f'POPUPFLAG unknown Popups.{m.group(1)} in {os.path.relpath(p,ROOT)}')
au=src[ROOT+'/java/com/myapp/app/ui/AppUi.kt']
actions=set(re.findall(r'var\s+(\w+)\s*:\s*\(',au))
for p,s in src.items():
    for m in re.finditer(r'\bAppActions\.(\w+)',s):
        if m.group(1) not in actions:
            issues.append(f'ACTION unknown AppActions.{m.group(1)} in {os.path.relpath(p,ROOT)}')
# every popup flag must be shown somewhere
allp=src[ROOT+'/java/com/myapp/app/ui/popup/PopupHooks.kt']
for p,s in src.items():
    pass
for fl in flags:
    used=sum(len(re.findall(r'Popups\.'+fl+r'\b',s)) for s in src.values())
    if used<2: issues.append(f'POPUPFLAG {fl} used only {used}x')
print('\n'.join(issues) if issues else 'NO ISSUES')
print(len(KT),'kotlin files,',len(issues),'findings')
