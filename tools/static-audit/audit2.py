import re,os,collections
ROOT='app/src/main/java'
files=[]
for d,_,fs in os.walk(ROOT):
    for f in fs:
        if f.endswith('.kt'): files.append(os.path.join(d,f))
src={p:open(p,encoding='utf-8').read() for p in files}
def strip(s):
    s=re.sub(r'"""(.|\n)*?"""','""',s)
    s=re.sub(r'"(\\.|[^"\\\n])*"','""',s)
    s=re.sub(r"'(\\.|[^'\\\n])'","''",s)
    s=re.sub(r'/\*.*?\*/','',s,flags=re.S)
    s=re.sub(r'//[^\n]*','',s)
    return s
clean={p:strip(s) for p,s in src.items()}

def match_paren(s,i):
    # s[i]=='(' -> index of matching ')'
    d=0
    for j in range(i,len(s)):
        c=s[j]
        if c in '([{': d+=1
        elif c in ')]}':
            d-=1
            if d==0: return j
    return -1

def split_top(args):
    out=[];d=0;cur=''
    for c in args:
        if c in '([{<' and c!='<': d+=1
        if c in ')]}': d-=1
        if c==',' and d==0:
            out.append(cur);cur=''
        else: cur+=c
    if cur.strip(): out.append(cur)
    return out

# collect function signatures (any indentation)
sigs=collections.defaultdict(list)   # name -> [ (file, [param names], hasVararg) ]
for p,s in clean.items():
    for m in re.finditer(r'\bfun\s+(?:<[^>]*>\s*)?(?:[\w.<>?]+\.)?(\w+)\s*\(',s):
        name=m.group(1)
        i=m.end()-1
        j=match_paren(s,i)
        if j<0: continue
        params=[]
        for part in split_top(s[i+1:j]):
            part=part.strip()
            part=re.sub(r'^(?:vararg|noinline|crossinline)\s+','',part)
            mm=re.match(r'(\w+)\s*:',part)
            if mm: params.append(mm.group(1))
        sigs[name].append((p,params))
# class constructors (class Foo(val a:..)) for named-arg calls
for p,s in clean.items():
    for m in re.finditer(r'\bclass\s+(\w+)\s*(?:<[^>]*>)?\s*\(',s):
        name=m.group(1)
        i=m.end()-1
        j=match_paren(s,i)
        if j<0: continue
        params=[]
        for part in split_top(s[i+1:j]):
            part=re.sub(r'^\s*(?:private|public|internal|override)?\s*(?:val|var)?\s*','',part.strip())
            mm=re.match(r'(\w+)\s*:',part)
            if mm: params.append(mm.group(1))
        sigs[name].append((p,params))

issues=[]
for p,s in clean.items():
    for name,lst in sigs.items():
        if len(name)<3: continue
        for m in re.finditer(r'(?<![\w.])(?:\w+\.)?'+re.escape(name)+r'\s*\(',s):
            # skip declarations
            pre=s[max(0,m.start()-10):m.start()]
            if re.search(r'\bfun\s+(?:<[^>]*>\s*)?(?:[\w.<>?]+\.)?$',s[max(0,m.start()-60):m.start()+len(m.group(0))-len(name)-1].split('\n')[-1]+' ') and False: pass
            line_start=s.rfind('\n',0,m.start())+1
            line=s[line_start:m.end()]
            if re.search(r'\bfun\b',line) or re.search(r'\bclass\b',line): continue
            i=m.end()-1
            j=match_paren(s,i)
            if j<0: continue
            args=split_top(s[i+1:j])
            named=[]
            for a in args:
                mm=re.match(r'\s*(\w+)\s*=(?!=)',a)
                if mm: named.append(mm.group(1))
            if not named: continue
            allowed=set()
            for _,params in lst: allowed|=set(params)
            for n in named:
                if n not in allowed:
                    ln=s[:m.start()].count('\n')+1
                    issues.append(f'NAMEDARG {os.path.relpath(p,ROOT)}:{ln}: {name}({n}=...) - params: {sorted(allowed)}')
print('\n'.join(sorted(set(issues))) if issues else 'NO NAMED-ARG ISSUES')
