import re,os,collections
exec(open('tools/static-audit/audit2.py').read().split('issues=[]')[0])
sig=collections.defaultdict(list)
for p,s in clean.items():
    for m in re.finditer(r'\bfun\s+(?:<[^>]*>\s*)?(?:([\w.<>?]+)\.)?(\w+)\s*\(',s):
        recv,name=m.group(1),m.group(2)
        i=m.end()-1; j=match_paren(s,i)
        if j<0: continue
        parts=[x.strip() for x in split_top(s[i+1:j]) if x.strip()]
        sig[name].append((p,parts,recv))
issues=[]
for name,lst in sig.items():
    if len(name)<3: continue
    # all overloads must have a non-function last param to flag
    bad=all(parts and '->' not in parts[-1] for _,parts,_ in lst)
    if not bad: continue
    for p,s in clean.items():
        for m in re.finditer(r'(?<![\w.])(?:\w+\.)?'+re.escape(name)+r'\s*\(',s):
            line_start=s.rfind('\n',0,m.start())+1
            line=s[line_start:m.end()]
            if re.search(r'\bfun\b|\bclass\b',line): continue
            i=m.end()-1; j=match_paren(s,i)
            if j<0: continue
            if re.match(r'\s*\{',s[j+1:j+40]) and not re.match(r'\s*\{\s*\n?\s*(else|catch)',s[j+1:j+20]):
                ln=s[:m.start()].count('\n')+1
                # statements like `if (x) {` are not calls of ours; require the call name not a keyword
                issues.append(f'TRAILING-LAMBDA {os.path.relpath(p,ROOT)}:{ln}: {name}(...) {{ }} but last param is "{lst[0][1][-1][:50]}"')
print('\n'.join(sorted(set(issues))) if issues else 'NO TRAILING-LAMBDA ISSUES')
