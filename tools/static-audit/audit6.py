import re,os,collections
exec(open('tools/static-audit/audit2.py').read().split('issues=[]')[0])
M={
'padding':'androidx.compose.foundation.layout.padding','size':'androidx.compose.foundation.layout.size',
'fillMaxWidth':'androidx.compose.foundation.layout.fillMaxWidth','fillMaxSize':'androidx.compose.foundation.layout.fillMaxSize',
'fillMaxHeight':'androidx.compose.foundation.layout.fillMaxHeight','height':'androidx.compose.foundation.layout.height',
'width':'androidx.compose.foundation.layout.width','widthIn':'androidx.compose.foundation.layout.widthIn',
'heightIn':'androidx.compose.foundation.layout.heightIn','offset':'androidx.compose.foundation.layout.offset',
'aspectRatio':'androidx.compose.foundation.layout.aspectRatio','clip':'androidx.compose.ui.draw.clip',
'background':'androidx.compose.foundation.background','border':'androidx.compose.foundation.border',
'clickable':'androidx.compose.foundation.clickable','alpha':'androidx.compose.ui.draw.alpha',
'shadow':'androidx.compose.ui.draw.shadow','rotate':'androidx.compose.ui.draw.rotate',
'verticalScroll':'androidx.compose.foundation.verticalScroll','rememberScrollState':'androidx.compose.foundation.rememberScrollState',
'Arrangement':'androidx.compose.foundation.layout.Arrangement','Alignment':'androidx.compose.ui.Alignment',
'FontWeight':'androidx.compose.ui.text.font.FontWeight','TextAlign':'androidx.compose.ui.text.style.TextAlign',
'RoundedCornerShape':'androidx.compose.foundation.shape.RoundedCornerShape','CircleShape':'androidx.compose.foundation.shape.CircleShape',
'Color':'androidx.compose.ui.graphics.Color','remember':'androidx.compose.runtime.remember',
'mutableStateOf':'androidx.compose.runtime.mutableStateOf','mutableIntStateOf':'androidx.compose.runtime.mutableIntStateOf',
'LaunchedEffect':'androidx.compose.runtime.LaunchedEffect','DisposableEffect':'androidx.compose.runtime.DisposableEffect',
'Composable':'androidx.compose.runtime.Composable','Modifier':'androidx.compose.ui.Modifier',
'Box':'androidx.compose.foundation.layout.Box','Column':'androidx.compose.foundation.layout.Column','Row':'androidx.compose.foundation.layout.Row',
'Spacer':'androidx.compose.foundation.layout.Spacer','BoxWithConstraints':'androidx.compose.foundation.layout.BoxWithConstraints',
'IntrinsicSize':'androidx.compose.foundation.layout.IntrinsicSize','MutableInteractionSource':'androidx.compose.foundation.interaction.MutableInteractionSource',
'Canvas':'androidx.compose.foundation.Canvas','Brush':'androidx.compose.ui.graphics.Brush','Offset':'androidx.compose.ui.geometry.Offset',
'Size':'androidx.compose.ui.geometry.Size','SolidColor':'androidx.compose.ui.graphics.SolidColor','TextFieldValue':'androidx.compose.ui.text.input.TextFieldValue',
'KeyboardType':'androidx.compose.ui.text.input.KeyboardType','BasicTextField':'androidx.compose.foundation.text.BasicTextField',
'BasicText':'androidx.compose.foundation.text.BasicText','TextOverflow':'androidx.compose.ui.text.style.TextOverflow',
'AnnotatedString':'androidx.compose.ui.text.AnnotatedString','buildAnnotatedString':'androidx.compose.ui.text.buildAnnotatedString',
'SpanStyle':'androidx.compose.ui.text.SpanStyle','withStyle':'androidx.compose.ui.text.withStyle','Dp':'androidx.compose.ui.unit.Dp',
'dp':'androidx.compose.ui.unit.dp','key':'androidx.compose.runtime.key','Animatable':'androidx.compose.animation.core.Animatable',
'tween':'androidx.compose.animation.core.tween','animateFloatAsState':'androidx.compose.animation.core.animateFloatAsState',
'animateDpAsState':'androidx.compose.animation.core.animateDpAsState','animateColorAsState':'androidx.compose.animation.animateColorAsState',
'BackHandler':'androidx.activity.compose.BackHandler','LocalContext':'androidx.compose.ui.platform.LocalContext',
'LocalDensity':'androidx.compose.ui.platform.LocalDensity','Layout':'androidx.compose.ui.layout.Layout','layout':'androidx.compose.ui.layout.layout',
'graphicsLayer':'androidx.compose.ui.graphics.graphicsLayer','drawWithContent':'androidx.compose.ui.draw.drawWithContent',
'clipToBounds':'androidx.compose.ui.draw.clipToBounds','onFocusChanged':'androidx.compose.ui.focus.onFocusChanged',
'pointerInput':'androidx.compose.ui.input.pointer.pointerInput','StrokeCap':'androidx.compose.ui.graphics.StrokeCap',
'Stroke':'androidx.compose.ui.graphics.drawscope.Stroke','ImeAction':'androidx.compose.ui.text.input.ImeAction',
'FocusRequester':'androidx.compose.ui.focus.FocusRequester','focusRequester':'androidx.compose.ui.focus.focusRequester',
'rememberCoroutineScope':'androidx.compose.runtime.rememberCoroutineScope','rememberInfiniteTransition':'androidx.compose.animation.core.rememberInfiniteTransition',
'Constraints':'androidx.compose.ui.unit.Constraints','IntOffset':'androidx.compose.ui.unit.IntOffset','CompositionLocalProvider':'androidx.compose.runtime.CompositionLocalProvider',
'HorizontalPager':'androidx.compose.foundation.pager.HorizontalPager','rememberPagerState':'androidx.compose.foundation.pager.rememberPagerState',
'delay':'kotlinx.coroutines.delay','launch':'kotlinx.coroutines.launch','withContext':'kotlinx.coroutines.withContext',
}
issues=[]
for p,s in clean.items():
    raw=src[p]
    imports=set(re.findall(r'^import\s+([\w.]+)(?:\s+as\s+(\w+))?\s*$',raw,re.M))
    imported={ (a or b.split('.')[-1] if False else None) for a,b in [] }
    names={}
    for full,alias in imports:
        names[alias or full.split('.')[-1]]=full
    stars=set(re.findall(r'^import\s+([\w.]+)\.\*\s*$',raw,re.M))
    pk=re.search(r'^package\s+([\w.]+)',s,re.M).group(1)
    for name,fqn in M.items():
        pat=r'(?<![\w.])'+re.escape(name)+r'\b'
        usage=False
        if name in ('padding','size','height','width','offset','alpha','shadow','rotate','clip','background','border','clickable','fillMaxWidth','fillMaxSize','fillMaxHeight','widthIn','heightIn','aspectRatio','verticalScroll','graphicsLayer','drawWithContent','clipToBounds','onFocusChanged','pointerInput','focusRequester','layout'):
            usage=bool(re.search(r'\.'+name+r'\s*[({]',s)) and not re.search(r'\bfun\s+(?:Modifier\.)?'+name+r'\b',s)
        elif name=='dp':
            usage=bool(re.search(r'\d\.dp\b|\)\.dp\b|\w\.dp\b',s))
        elif name in ('key',):
            usage=bool(re.search(r'(?<![\w.])key\s*\(',s))
        elif name in ('launch','delay'):
            usage=bool(re.search(r'(?<![\w.])'+name+r'\s*[({]',s))
        else:
            usage=bool(re.search(pat,s))
        if not usage: continue
        pkgname=fqn.rsplit('.',1)[0]
        if name in names and names[name]==fqn: continue
        if pkgname in stars: continue
        # declared in same file / aliased differently / member of scope (launch on scope is member!)
        if name in ('launch',) : continue
        if name=='key' : continue
        issues.append(f'MISSING-IMPORT {os.path.relpath(p,ROOT)}: {name} (needs {fqn})')
    # delegated property helpers
    if re.search(r'\bby\s+(remember|mutableStateOf|mutableIntStateOf|animate\w+AsState|collectIsPressedAsState|rememberInfiniteTransition)',s) or re.search(r'\bby\s+\w+\.animateFloat',s) or re.search(r'\bby\s+interaction',s):
        if 'getValue' not in names and 'androidx.compose.runtime' not in stars:
            issues.append(f'MISSING-IMPORT {os.path.relpath(p,ROOT)}: getValue (androidx.compose.runtime.getValue)')
    if re.search(r'\bvar\s+\w+\s+by\s+(remember|mutableStateOf|mutableIntStateOf)',s):
        if 'setValue' not in names and 'androidx.compose.runtime' not in stars:
            issues.append(f'MISSING-IMPORT {os.path.relpath(p,ROOT)}: setValue (androidx.compose.runtime.setValue)')
print('\n'.join(sorted(set(issues))) if issues else 'NO MISSING-IMPORT ISSUES')
