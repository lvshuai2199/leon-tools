import json,sys
from playwright.sync_api import sync_playwright
U="file:///workspace/cnc-v3/tmp/cnc-prototype.fix1004.html"
R=[];errs=[]
def ok(n,c,d=""):
  R.append(("PASS" if c else "FAIL",n,str(d)[:300])); print(*R[-1],sep=" | ",flush=True)
with sync_playwright() as p:
  b=p.chromium.launch()
  def mk(w,h):
    ctx=b.new_context(viewport={"width":w,"height":h},locale="zh-CN",has_touch=True); pg=ctx.new_page(); pg.set_default_timeout(3000)
    pg.on("pageerror",lambda e:errs.append(f"{w}:{e} | {(e.stack or '')[:300]}")); pg.on("dialog",lambda d:d.accept())
    return pg
  pg=mk(1280,800); J=lambda js:pg.evaluate(js); M="__cnc.M."
  toast=lambda: J("document.getElementById('toastT').textContent")
  mtxt=lambda: J("document.getElementById('modal')?document.getElementById('modal').innerText:''")
  mopen=lambda: J("document.getElementById('mask').classList.contains('show')||getComputedStyle(document.getElementById('mask')).display!=='none'&&document.getElementById('mask').offsetParent!==null")
  def fresh(page=None):
    pg.goto(U); J("Object.keys(localStorage).filter(k=>k.startsWith('cnc.v2.')).forEach(k=>localStorage.removeItem(k))"); pg.goto(U); pg.wait_for_timeout(200)
    J("__cnc.S.tickMs=1;__cnc.SIG.TA.v=1;__cnc.SIG.TB.v=1;__cnc.S.fast=true;__cnc.renderAll()")
    if page: J(f"__cnc.show('{page}')"); pg.wait_for_timeout(150)
  def btn_in_modal(t): pg.locator('#modal button',has_text=t).last.click(); pg.wait_for_timeout(120)

  # ---- M-02 / M-03 / M-18 模块选择区
  fresh('proj')
  ok("M-24 1280 模块选择区存在且宽 280",J("Math.round(document.getElementById('eLib').getBoundingClientRect().width)")==280,J("document.getElementById('eLib').getBoundingClientRect().width"))
  ok("M-03 没选节点 → 提示先点节点",'先在画布上点' in J("document.getElementById('ePkT').innerText"))
  pg.locator('#eLib .pk-add').first.click(force=True); pg.wait_for_timeout(100)
  ok("M-03 没选节点点加入 → toast 提示",'先在画布上点' in toast(),toast())
  pg.click('#n-init'); pg.wait_for_timeout(120)
  ok("M-02 选中初始化 → 顶部写加入目标",'初始化' in J("document.getElementById('ePkT').innerText"),J("document.getElementById('ePkT').innerText"))
  pg.fill('#ePkQ','不存在的xyz'); pg.wait_for_timeout(120)
  ok("M-02 搜不到 → 无结果提示 + 清除搜索",'没有找到' in J("document.getElementById('ePkW').innerText") and pg.locator('#ePkClr').count()==1)
  pg.click('#ePkClr'); pg.wait_for_timeout(100)
  nm=J("__cnc.DB.progs[1].n"); frag=nm[:2]
  pg.fill('#ePkQ',frag); pg.wait_for_timeout(120)
  names=J("[...document.querySelectorAll('#ePkW .mrow b')].map(x=>x.textContent)")
  ok("M-02 名字片段搜索只列匹配项",names and all(frag in n for n in names),(frag,names))
  pg.fill('#ePkQ',''); pg.wait_for_timeout(100)
  pid=J("__cnc.DB.progs[1].id")
  pg.locator(f'#ePkW [data-prog="{pid}"]').click(); pg.wait_for_timeout(120)
  ok("M-02 点加入 → 加到初始化节点",J(f"__cnc.DB.proj.mods.init.calls.includes('{pid}')"),toast())
  ok("M-18 已加入的模块显示「已加入」，不能重复加",pg.locator(f'#ePkW [data-prog="{pid}"]').count()==0 and '已加入' in J("document.getElementById('ePkW').innerText"))
  pg.click('#ePkTab [data-v="t"]') if pg.locator('#ePkTab [data-v="t"]').count() else pg.locator('#ePkTab button',has_text='料盘').click()
  pg.wait_for_timeout(100)
  ok("M-24 料盘标签列出料盘，已选的写「已加入」",J("document.getElementById('ePkW').innerText").count('已加入')==2,J("document.getElementById('ePkW').innerText")[:120])
  ok("M-02 保存键有改动后可用",not J("document.getElementById('eSave').classList.contains('dis')"))

  # ---- M-05 / M-21 / M-22 红黄状态（界面）
  fresh('prog')
  J("__cnc.DB.proj.mods.init.calls=['P1'];__cnc.M.clearAffected();__cnc.S.verified=true;__cnc.renderAll()")
  J("__cnc.show('proj')"); pg.wait_for_timeout(80); J("__cnc.dirty.proj=false")
  J("__cnc.show('prog')"); pg.wait_for_timeout(100)
  ok("M-16 没有改动时保存键置灰",J("document.getElementById('rSave').classList.contains('dis')"))
  J("var i=__cnc.DB.progs.findIndex(p=>p.id=='P1');__cnc.R.p=i;__cnc.DB.progs[i].steps.push({ty:'wait',how:'时长',sec:7});__cnc.renderAll()")
  pg.click('#rSave'); pg.wait_for_timeout(120); t1=toast()
  J("__cnc.show('proj')"); pg.wait_for_timeout(150)
  cls=J("document.getElementById('n-init').className")
  ok("M-05 界面：初始化节点黄标，其他节点不标",' upd' in ' '+cls and J("document.querySelectorAll('#pg-proj .node.upd,#pg-proj .pill.warn').length")==1,(cls,t1))
  ok("M-20/M-21 黄标节点写「程序已更新」并带来源",'更新' in J("document.getElementById('n-init').innerText"),J("document.getElementById('n-init').innerText"))
  J("__cnc.show('home')"); pg.wait_for_timeout(100)
  ok("M-05 首页开始禁用 + 按钮下写原因",J("document.getElementById('bStart').disabled") and J("document.getElementById('bStartWhy').innerText")!='',J("document.getElementById('bStartWhy').innerText"))
  J("__cnc.DB.proj.mods.mc.calls=['P90'];__cnc.renderAll()")
  ok("M-22 红黄同时 → 首页原因只写红色",'不存在' in J("document.getElementById('bStartWhy').innerText") and '更新' not in J("document.getElementById('bStartWhy').innerText"),J("document.getElementById('bStartWhy').innerText"))
  J("__cnc.show('proj')"); pg.wait_for_timeout(120)
  ok("M-11 界面：机床换料节点红标",' bad' in ' '+J("document.getElementById('n-mc').className"),J("document.getElementById('n-mc').className"))
  J("__cnc.DB.proj.mods.mc.calls=['P3'];__cnc.renderAll()"); J("__cnc.show('home')"); pg.wait_for_timeout(100)
  ok("M-22 修好红的 → 显示黄的原因",'更新' in J("document.getElementById('bStartWhy').innerText") or '核对' in J("document.getElementById('bStartWhy').innerText"),J("document.getElementById('bStartWhy').innerText"))

  # ---- L-23 / L-27 切换确认弹窗
  fresh('proj')
  J("['A','B'].forEach(i=>__cnc.M.removeTrayFromProject(i));var M=__cnc.DB.proj.mods;['pick','unload'].forEach(k=>{M[k].src='料框';M[k].ref='成品料框1';M[k].trays=[]});M.pick.calls=['P1'];__cnc.M.genLoop();__cnc.renderAll()")
  pg.click('#eSave'); pg.wait_for_timeout(120)
  ok("准备：手动编排",J("__cnc.DB.proj.loop.auto")==False)
  before=J("JSON.stringify([__cnc.DB.proj.mods,__cnc.DB.proj.order])")
  pg.click('#n-pick'); pg.wait_for_timeout(100)
  pg.locator('#ePkTab button',has_text='料盘').click(); pg.wait_for_timeout(100)
  pg.locator('#ePkW [data-tray="A"]').click(); pg.wait_for_timeout(200)
  sw=mtxt()
  ok("L-23 弹出「将切换为料盘自动主循环」正式弹窗（不是 confirm）",'将切换为料盘自动主循环' in sw and pg.locator('#swTbl').count()==1,sw[:80])
  ok("L-27 表：取毛坯上的模块写移到取毛坯后，空节点写不用移",'「取毛坯」后面的插入位' in sw and '不用移' in sw and '点「取消」就不选这个料盘' in sw,sw)
  ww=J("Math.round(document.getElementById('modal').getBoundingClientRect().width)")
  ok("L-23 弹窗宽 480，按钮高 56",ww==480 and J("Math.round(document.querySelector('#modal #swOk, #modal .btn.pri').getBoundingClientRect().height)")==56,ww)
  btn_in_modal('取消')
  ok("L-23 取消 → 料盘没选、编排不变",J("JSON.stringify([__cnc.DB.proj.mods,__cnc.DB.proj.order])")==before and J("__cnc.DB.proj.loop.auto")==False)
  pg.click('#n-pick'); pg.wait_for_timeout(80); pg.locator('#ePkTab button',has_text='料盘').click(); pg.wait_for_timeout(80)
  pg.locator('#ePkW [data-tray="A"]').click(); pg.wait_for_timeout(200); pg.click('#swOk'); pg.wait_for_timeout(200)
  ins=json.loads(J("JSON.stringify(__cnc.DB.proj.loop.ins)"))
  ok("L-23 切换 → 主循环，取毛坯的模块在「取毛坯后」",J("__cnc.DB.proj.loop.auto") and {'t':'prog','id':'P1'} in ins['post:pick'],ins['post:pick'])
  ok("L-24 主循环骨架锁定显示（5 个阶段）",J("document.querySelectorAll('#eLoop .stage, #eLoop [data-stage]').length")>=5 or '选盘' in J("document.getElementById('pg-proj').innerText"))
  # 取消全部料盘：弹窗提示待核对
  J("__cnc.S.verified=true")
  pg.locator('#ePkTab button',has_text='料盘').click(); pg.wait_for_timeout(80)
  J("window.__dsel=typeof deselectDialog")
  # ---- 删除时的引用拦截
  fresh('prog'); J("__cnc.R.p=__cnc.DB.progs.findIndex(p=>p.id=='P3');__cnc.renderAll()"); pg.wait_for_timeout(80)
  pg.click('#rDel'); pg.wait_for_timeout(150); dt=mtxt()
  n=len(J(M+"refsTo('prog','P3')"))
  ok("M-09 界面：删被引用程序 → 弹窗写被 N 处引用并列位置",'不能删除' in dt and f'被 {n} 处引用' in dt.replace('\n',' ') and pg.locator('#refList [data-ref]').count()==n,(n,dt[:160]))
  pg.locator('#refList [data-ref]').first.click(); pg.wait_for_timeout(200)
  ok("M-09 点引用位置能跳过去",J("__cnc.S.page")=='proj',J("__cnc.S.page"))
  fresh('tray'); pg.click('#tMore'); pg.wait_for_timeout(100); pg.click('#tmDel'); pg.wait_for_timeout(150); dt=mtxt()
  ok("L-20 界面：删被工程选中的料盘 → 拦下列位置",'引用' in dt and ('取毛坯' in dt or '成品下料' in dt),dt[:160])
  # ---- 重名：保存时拦
  fresh('prog'); nm0=J("__cnc.DB.progs[0].n")
  pg.click('#rNew'); pg.wait_for_timeout(100); pg.fill('#npN',' '+nm0+' '); btn_in_modal('创建')
  pg.click('#rSave'); pg.wait_for_timeout(120)
  ok("M-17 新建重名（带空格）→ 保存时拦下提示",'重名' in toast() or '同名' in toast() or '重名' in J("document.getElementById('pg-prog').innerText"),toast())
  ok("M-17 被拦后没写进本地",J("(JSON.parse(localStorage.getItem('cnc.v2.prog')||'null')||{d:[[]]}).d[0].filter(p=>p.n.trim()==%s).length"%json.dumps(nm0))<=1)
  fresh('prog'); pg.locator('#rList .pi').nth(1).click(); pg.wait_for_timeout(100); pg.click('#rRen'); pg.wait_for_timeout(100); pg.fill('#rnN',nm0); btn_in_modal('改名')
  ok("M-17 改名成已有名字 → 提示",J("(document.getElementById('rnE')||{}).textContent||''")!='' or '重名' in toast() or '同名' in toast(),J("(document.getElementById('rnE')||{}).textContent||''"))
  # ---- L-29
  fresh(); pg.click('#logo'); pg.wait_for_timeout(150)
  ok("L-29 未绑定 → 演示「结束信号」按钮置灰并提示",J("document.getElementById('dmEnd').disabled") and '先在信号页绑定结束信号' in J("document.getElementById('dmEndHint')?document.getElementById('dmEndHint').innerText:''"),J("document.getElementById('dmEndHint')?document.getElementById('dmEndHint').innerText:''"))


  # ---- fix1004：示例工程去向表 5 行 + 每次都弹
  fresh('proj')
  J("['A','B'].forEach(i=>__cnc.M.removeTrayFromProject(i));__cnc.renderAll()"); pg.wait_for_timeout(100)
  pops=[]; first=''
  for rnd in range(3):
    pg.click('#n-pick'); pg.wait_for_timeout(80); pg.locator('#ePkTab button',has_text='料盘').click(); pg.wait_for_timeout(80)
    pg.locator('#ePkW [data-tray="A"]').click(); pg.wait_for_timeout(200)
    t=mtxt(); pops.append('将切换为料盘自动主循环' in t)
    if rnd==0: first=t; lw=J("(()=>{const c=document.querySelector('#swTbl td, #swTbl .k, #swTbl th');return c?Math.round(c.getBoundingClientRect().width):0})()")
    if pops[-1]: pg.click('#swOk'); pg.wait_for_timeout(150)
    J("['A','B'].forEach(i=>__cnc.M.removeTrayFromProject(i));__cnc.renderAll()"); pg.wait_for_timeout(100)
  keys=['机床换料上的模块','移到「机床换料」后面的插入位','取毛坯、成品下料','不用移','二次定位节点','整个节点移到「取毛坯」后面的插入位','翻转台节点','整个节点移到「机床换料」后面的插入位','排在','初始化、收尾','不动']
  miss=[k for k in keys if k not in first]
  ok("L-27 示例工程去向表是美工的 5 行",not miss and '其他节点' not in first,(miss,first[:400]))
  ok("L-27 去向表左列宽 128",lw==128,lw)
  ok("验收15 连续 3 轮去掉料盘再加上，每次都弹",all(pops) and len(pops)==3,pops)
  # ---- 1024 抽屉 M-23 / M-14
  pg2=mk(1024,640); pg=pg2; J=lambda js:pg.evaluate(js)
  fresh('proj'); pg.click('#n-init'); pg.wait_for_timeout(200)
  dr=J("(()=>{const d=document.getElementById('eProps');const r=d.getBoundingClientRect();return {w:Math.round(r.width),open:r.width>0&&getComputedStyle(d).display!=='none',bottom:r.bottom}})()")
  ok("M-23 1024 点节点开抽屉，宽 360",dr['open'] and dr['w']==360,dr)
  if pg.locator('#eAddCall').count(): pg.click('#eAddCall'); pg.wait_for_timeout(150)
  ok("M-23 「+ 调用模块」切到选择区",pg.locator('#eProps #ePkQ').count()==1)
  qh=J("document.querySelector('#eProps #ePkQ')?Math.round(document.querySelector('#eProps #ePkQ').closest('label').getBoundingClientRect().height):0")
  ok("M-14 搜索框高 ≥ 48",qh>=48,qh)
  stop=J("(()=>{const s=document.getElementById('bStop'),d=document.getElementById('eProps');if(!s||!s.offsetParent)return 'nostop';const a=s.getBoundingClientRect(),b=d.getBoundingClientRect();return !(a.left<b.right&&a.right>b.left&&a.top<b.bottom&&a.bottom>b.top)})()")
  ok("M-23/G-16 抽屉不盖 [停止]",stop in (True,'nostop'),stop)
  ok("无页面报错",not errs,errs[:3])
  b.close()
print("\nTOTAL",len(R),"PASS",sum(1 for x in R if x[0]=="PASS"),"FAIL",sum(1 for x in R if x[0]=="FAIL"))
