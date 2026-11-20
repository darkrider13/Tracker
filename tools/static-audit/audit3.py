import re,os,collections
exec(open('tools/static-audit/audit2.py').read().split('issues=[]')[0])
# richer signatures: keep default info
full=collections.defaultdict(list)
for p,s in clean.items():
    for m in re.finditer(r'\bfun\s+(?:<[^>]*>\s*)?(?:([\w.<>?]+)\.)?(\w+)\s*\(',s):
        recv,name=m.group(1),m.group(2)
        i=m.end()-1
        j=match_paren(s,i)
        if j<0: continue
        ps=[]
        for part in split_top(s[i+1:j]):
            part=part.strip()
            part=re.sub(r'^(?:vararg|noinline|crossinline)\s+','',part)
            mm=re.match(r'(\w+)\s*:',part)
            if mm:
                # default if '=' at depth 0 after the type
                d=0;has=False
                for c in part[mm.end():].replace('->','~'):
                    if c in '([{<': d+=1
                    elif c in ')]}>': d-=1
                    elif c=='=' and d==0: has=True;break
                ps.append((mm.group(1),has))
        full[name].append((p,ps,recv))
issues=[]
for name,lst in full.items():
    if len(lst)!=1 or len(name)<4: continue   # skip overloaded / tiny names
    f,ps,recv=lst[0]
    if recv: continue                          # extension functions: receiver confuses arg counting
    required=[n for n,has in ps if not has]
    if not required: continue
    for p,s in clean.items():
        for m in re.finditer(r'(?<![\w.])(?:\w+\.)?'+re.escape(name)+r'\s*\(',s):
            line_start=s.rfind('\n',0,m.start())+1
            line=s[line_start:m.end()]
            if re.search(r'\bfun\b|\bclass\b',line): continue
            i=m.end()-1
            j=match_paren(s,i)
            if j<0: continue
            args=split_top(s[i+1:j])
            named={re.match(r'\s*(\w+)\s*=(?!=)',a).group(1) for a in args if re.match(r'\s*(\w+)\s*=(?!=)',a)}
            positional=len([a for a in args if not re.match(r'\s*(\w+)\s*=(?!=)',a)])
            trailing = re.match(r'\s*\{',s[j+1:j+4]) is not None or re.match(r'\s*\n\s*\{',s[j+1:j+30]) is not None
            provided=set(named)
            for k in range(positional):
                if k<len(ps): provided.add(ps[k][0])
            if trailing and ps: provided.add(ps[-1][0])
            miss=[n for n in required if n not in provided]
            if miss:
                ln=s[:m.start()].count('\n')+1
                issues.append(f'MISSING-ARG {os.path.relpath(p,ROOT)}:{ln}: {name}() lacks {miss}')
print('\n'.join(sorted(set(issues))) if issues else 'NO MISSING-ARG ISSUES')
