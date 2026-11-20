import re,os,collections
exec(open('tools/static-audit/audit2.py').read().split('issues=[]')[0])
full=collections.defaultdict(list)
for p,s in clean.items():
    for m in re.finditer(r'\bfun\s+(?:<[^>]*>\s*)?(?:([\w.<>?]+)\.)?(\w+)\s*\(',s):
        recv,name=m.group(1),m.group(2)
        i=m.end()-1; j=match_paren(s,i)
        if j<0: continue
        parts=[x.strip() for x in split_top(s[i+1:j]) if x.strip()]
        full[name].append((p,parts,recv))
issues=[]
for name,lst in full.items():
    if len(name)<3 or len(lst)!=1 or lst[0][2]: continue
    p0,parts,_=lst[0]
    n=len(parts)
    for p,s in clean.items():
        for m in re.finditer(r'(?<![\w.])(?:\w+\.)?'+re.escape(name)+r'\s*\(',s):
            line_start=s.rfind('\n',0,m.start())+1
            line=s[line_start:m.end()]
            if re.search(r'\bfun\b|\bclass\b',line): continue
            i=m.end()-1; j=match_paren(s,i)
            if j<0: continue
            args=split_top(s[i+1:j])
            pos=[a for a in args if not re.match(r'\s*\w+\s*=(?!=)',a)]
            trailing=1 if re.match(r'\s*\{',s[j+1:j+30]) else 0
            # positional args after a named arg are rare; compare counts
            if len(pos)+len([a for a in args if re.match(r'\s*\w+\s*=(?!=)',a)])+trailing>n:
                ln=s[:m.start()].count('\n')+1
                issues.append(f'TOO-MANY-ARGS {os.path.relpath(p,ROOT)}:{ln}: {name} takes {n}, got {len(args)}+{trailing}')
            # positional types: check first positional against declared type keywords when obviously different
            for k,a in enumerate(pos):
                if k>=n: break
                ptype=parts[k].split(':',1)[1].split('=')[0].strip() if ':' in parts[k] else ''
                a=a.strip()
                if ptype in('Dp',) and not re.search(r'\.dp\b|Dp|dp\b|width|size|radius|pad',a): issues.append(f'ARG-TYPE? {os.path.relpath(p,ROOT)}:{s[:m.start()].count(chr(10))+1}: {name} arg{k} expects Dp got `{a[:30]}`')
                if ptype=='String' and re.match(r'^-?\d+(\.\d+)?f?$',a): issues.append(f'ARG-TYPE? {os.path.relpath(p,ROOT)}:{s[:m.start()].count(chr(10))+1}: {name} arg{k} expects String got `{a}`')
                if ptype in('Float',) and re.match(r'^\d+\.dp$',a): issues.append(f'ARG-TYPE? {os.path.relpath(p,ROOT)}:{s[:m.start()].count(chr(10))+1}: {name} arg{k} expects Float got `{a}`')
                if ptype=='Dp' and re.match(r'^-?\d+(\.\d+)?f?$',a): issues.append(f'ARG-TYPE? {os.path.relpath(p,ROOT)}:{s[:m.start()].count(chr(10))+1}: {name} arg{k} expects Dp got `{a}`')
print('\n'.join(sorted(set(issues))) if issues else 'NO ARG-COUNT/TYPE ISSUES')
