import re,os,collections
exec(open('tools/static-audit/audit2.py').read().split('issues=[]')[0])
models=clean[ROOT+'/com/myapp/app/data/Models.kt']
def fields(cls):
    m=re.search(r'\bclass\s+'+cls+r'\s*\(',models)
    i=m.end()-1; j=match_paren(models,i)
    out=set()
    for part in split_top(models[i+1:j]):
        mm=re.search(r'\b(?:val|var)\s+(\w+)',part)
        if mm: out.add(mm.group(1))
    # members declared in the class body
    k=models.find('{',j)
    return out
F={c:fields(c) for c in ['Debt','Transaction','DebtPayment','FundMeta','AppState','Filters']}
print({k:len(v) for k,v in F.items()})
issues=[]
STD={'let','also','apply','run','copy','equals','hashCode','toString','isNullOrEmpty','isNotEmpty','isEmpty','isNullOrBlank','isNotBlank','length','size','forEach','filter','map','sumOf','firstOrNull','any','none','first','last','contains','add','remove','get','set','keys','values','entries','containsKey','toList','toMutableList','sortedBy','sortedByDescending','sortedWith','count','takeIf','ifEmpty','plus','minus'}
# AppStore.state.X
for p,s in clean.items():
    for m in re.finditer(r'AppStore\.state\.(\w+)',s):
        n=m.group(1)
        if n not in F['AppState'] and n not in STD:
            issues.append(f'APPSTATE? {os.path.relpath(p,ROOT)}:{s[:m.start()].count(chr(10))+1}: state.{n}')
# local variables d/entry/e of Debt type (only in debt files)
dfiles=['DebtsPane','AddDebtPopup','DebtPaymentPopup','DebtHistoryPopup','Reminders','DebtState','FundDetailsScreen','EditFundPopup','StatsScreen']
for p,s in clean.items():
    base=os.path.basename(p)[:-3]
    if base in dfiles:
        for m in re.finditer(r'(?<![\w.])(d|entry|e)\.(\w+)\b(?!\()',s):
            n=m.group(2)
            if n not in F['Debt'] and n not in STD:
                issues.append(f'DEBTFIELD? {base}:{s[:m.start()].count(chr(10))+1}: {m.group(1)}.{n}')
    if base in ('LogsScreen','LogsState','FundDetailsScreen','AddDebtPopup','DebtPaymentPopup','StatsScreen'):
        for m in re.finditer(r'(?<![\w.])(t|tx)\.(\w+)\b(?!\()',s):
            n=m.group(2)
            if n not in F['Transaction'] and n not in STD:
                issues.append(f'TXFIELD? {base}:{s[:m.start()].count(chr(10))+1}: {m.group(1)}.{n}')
# payments
for p,s in clean.items():
    for m in re.finditer(r'(?<![\w.])(pm|p|record)\.(\w+)\b(?!\()',s):
        n=m.group(2)
        base=os.path.basename(p)[:-3]
        if base in ('DebtPaymentPopup','DebtHistoryPopup','AddDebtPopup','EditFundPopup') and n not in F['DebtPayment'] and n not in STD:
            issues.append(f'PAYFIELD? {base}:{s[:m.start()].count(chr(10))+1}: {m.group(1)}.{n}')
# return@label sanity
for p,s in clean.items():
    for m in re.finditer(r'return@(\w+)',s):
        lab=m.group(1)
        if not re.search(r'(?<![\w])'+lab+r'\s*[({<]|\.'+lab+r'\s*[({]|'+lab+r'\s*\n?\s*\{',s[:m.start()]):
            issues.append(f'LABEL? {os.path.relpath(p,ROOT)}:{s[:m.start()].count(chr(10))+1}: return@{lab}')
print('\n'.join(sorted(set(issues))) if issues else 'NO MEMBER/LABEL ISSUES')
