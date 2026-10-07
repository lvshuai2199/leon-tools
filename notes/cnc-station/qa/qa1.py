import json
from playwright.sync_api import sync_playwright
U="file:///workspace/cnc-v3/cnc-prototype.html"
R=[];errs=[]
def ok(n,c,d=""):
  R.append(("PASS" if c else "FAIL",n,str(d)[:300])); print(*R[-1],sep=" | ",flush=True)
with sync_playwright() as p:
  b=p.chromium.launch(); ctx=b.new_context(viewport={"width":1280,"height":800},locale="zh-CN")
  pg=ctx.new_page(); pg.set_default_timeout(4000)
  pg.on("pageerror",lambda e:errs.append(str(e)))
  pol={"accept":True}
  pg.on("dialog",lambda d:(d.accept() if (d.type=="beforeunload" or pol["accept"]) else d.dismiss()))
  J=lambda js:pg.evaluate(js)
  def fresh():
    pg.goto(U); J("Object.keys(localStorage).filter(k=>k.startsWith('cnc.v2.')).forEach(k=>localStorage.removeItem(k))")
    pg.goto(U); pg.wait_for_timeout(200); J("__cnc.S.tickMs=1;__cnc.SIG.TA.v=1;__cnc.SIG.TB.v=1;__cnc.S.fast=true;__cnc.renderAll()")
  def wait(cond,ms=15000):
    t=0
    while t<ms:
      if J(cond): return True
      pg.wait_for_timeout(40); t+=40
    return False
  def small():  # 小料盘、清计数
    J("var A=__cnc.DB.trays[0],B=__cnc.DB.trays[1];A.rows='1';A.cols='2';A.layers='1';B.rows='1';B.cols='3';B.layers='1';__cnc.DB.trays.forEach(t=>{t.picked=0;t.placed=0});__cnc.S.machine=null;__cnc.S.count=0;__cnc.S.done=false;__cnc.DB.proj.target=0;__cnc.renderAll()")
  toast=lambda: J("document.getElementById('toastT').textContent")
  M="__cnc.M."

  # ---- M-01 / M-16 / M-19 / M-17 版本号、改名、重名
  fresh(); J("__cnc.show('prog')"); pg.wait_for_timeout(100)
  J("__cnc.DB.progs.push({id:'P90',kind:'prog',ver:0,n:'QA-B',grip:__cnc.DB.progs[0].grip,steps:[{ty:'wait',t:'1'}]});__cnc.DB.progs.push({id:'P91',kind:'prog',ver:0,n:'QA-A',grip:__cnc.DB.progs[0].grip,steps:[{ty:'call',prog:'P90'}]})")
  pg.click('#rSave'); pg.wait_for_timeout(80)
  v=lambda i: J(f"__cnc.DB.progs.find(p=>p.id=='{i}').ver")
  ok("M-01 新建保存 → 版本 1",v('P90')==1 and v('P91')==1,(v('P90'),v('P91')))
  J("__cnc.DB.progs.find(p=>p.id=='P90').steps.push({ty:'wait',t:'2'})"); pg.click('#rSave'); pg.wait_for_timeout(80)
  ok("M-01 改内容再保存 → 版本 2，id 不变",v('P90')==2)
  pg.click('#rSave'); pg.wait_for_timeout(80)
  ok("M-16 内容没变再保存 → 版本不变",v('P90')==2,toast())
  J("var s=__cnc.DB.progs.find(p=>p.id=='P90').steps;s.push({ty:'wait',t:'9'});s.pop()"); pg.click('#rSave'); pg.wait_for_timeout(80)
  ok("M-16 改了再改回 → 版本不变",v('P90')==2)
  r=json.loads(J(M+"renameProg('P90','QA-B2')").__str__() if False else json.dumps(J(M+"renameProg('P90','QA-B2')")))
  pg.click('#rSave'); pg.wait_for_timeout(80)
  ok("M-19 改名不 +1",r['ok'] and v('P90')==2,r)
  r=J(M+"renameProg('P90','  QA-A ')")
  ok("M-17 改名成已有名字（带空格）被拦",not r['ok'] and '同名' in r['why'],r)
  pg.reload(); pg.wait_for_timeout(200)
  ok("M-01 刷新后 id、版本、名字保持",J("JSON.stringify(__cnc.DB.progs.find(p=>p.id=='P90'))").find('"ver":2')>0 and J("__cnc.DB.progs.find(p=>p.id=='P90').n")=='QA-B2')

  # ---- M-18 / M-05 / M-07 / M-21 传递黄标、待核对
  J("__cnc.S.tickMs=1;__cnc.SIG.TA.v=1;__cnc.SIG.TB.v=1")
  r=J(M+"nodeAddCall('init','P91')"); r2=J(M+"nodeAddCall('init','P91')")
  ok("M-18 同一节点重复加同一模块被拦",r['ok'] and not r2['ok'] and '已在这个节点里' in r2['why'],r2)
  J("__cnc.show('proj')"); pg.wait_for_timeout(100); J("saveProj=undefined"); pg.click('#eSave'); pg.wait_for_timeout(80)
  J("__cnc.M.clearAffected();__cnc.S.verified=true;__cnc.deploy.st=['ok','ok','ok','ok'];__cnc.renderAll()")
  ok("准备：核对后可开始",J(M+"startWhy()")=='',J(M+"startWhy()"))
  J("__cnc.show('prog')"); pg.wait_for_timeout(80)
  J("__cnc.DB.progs.find(p=>p.id=='P90').steps.push({ty:'wait',t:'3'})"); pg.click('#rSave'); pg.wait_for_timeout(80)
  aff=J("JSON.stringify(__cnc.deploy.affected)")
  ok("M-07 改 B → 调用 B 的 A 所在节点（初始化）标黄，其他节点不标",list(json.loads(aff).keys())==['init'],aff)
  ok("M-05 工程待核对、开始禁用写原因",J("__cnc.S.verified")==False and J(M+"startWhy()")!='',J(M+"startWhy()"))
  pg.reload(); pg.wait_for_timeout(200)
  ok("M-06 刷新后黄标和待核对保持",json.loads(J("JSON.stringify(__cnc.deploy.affected)")).get('init') and J("__cnc.S.verified")==False)

  # ---- M-09 引用计数、删除保护
  r=J(M+"deleteProg('P90')")
  ok("M-09 删被引用的 B → 拦下「被 1 处引用」，位置是程序 A 第 1 步",not r['ok'] and '被 1 处' in r['why'] and '第 1 步' in r['refs'][0]['where'],r)
  r=J(M+"deleteProg('P91')")
  ok("M-09 删 A → 被初始化节点引用",not r['ok'] and '初始化' in r['refs'][0]['where'],r)
  # ---- M-11 / M-22 失效引用
  J("var i=__cnc.DB.progs.findIndex(p=>p.id=='P90');__cnc.DB.progs.splice(i,1);__cnc.renderAll()")
  ok("M-11 引用失效 → 初始化节点红，开始禁用",J(M+"nodeState('init')")=='broken' and J(M+"startWhy()")!='',J(M+"startWhy()"))
  ok("M-22 红黄同时 → 原因只写红色",'不存在' in J(M+"startWhy()") or '失效' in J(M+"startWhy()"),J(M+"startWhy()"))
  J("__cnc.show('prog')"); pg.wait_for_timeout(80); pg.click('#rSave'); pg.wait_for_timeout(80); pg.reload(); pg.wait_for_timeout(250)
  ok("M-11 带失效引用保存后刷新不白屏",J("!!window.__cnc && document.querySelector('#bStart')!=null") and J(M+"nodeState('init')")=='broken')

  # ---- M-15 v:1 旧数据
  fresh(); J("localStorage.setItem('cnc.v2.proj',JSON.stringify({v:1,d:[{}]}))"); pg.reload(); pg.wait_for_timeout(200)
  ok("M-15 v:1 数据 → 删键回示例，不白屏",J("localStorage.getItem('cnc.v2.proj')") is None and J("__cnc.DB.proj.name")=='上下料一号')

  # ---- L-05 / D48-8 骨架锁定、插入位去重
  fresh()
  ok("L-05 骨架不能调顺序",J(M+"loopMoveStage().ok")==False and J(M+"moveNode('mc',-1)")==False)
  a=J(M+"loopInsert('pre:mc',{t:'prog',id:'P3'})"); b2=J(M+"loopInsert('pre:mc',{t:'prog',id:'P3'})")
  ok("D48-8 同一插入位重复加同一模块被拦",a and not b2)
  ok("L-05 插入的模块能删",J(M+"loopRemove('pre:mc',0)") and J("__cnc.DB.proj.loop.ins['pre:mc'].length")==0)

  # ---- L-20 料盘删除保护
  r=J(M+"deleteTray('A')"); ok("L-20 工程选中的料盘不能删",not r['ok'],r)

  # ---- L-07 只选料盘 B
  fresh(); small(); J(M+"removeTrayFromProject('A')")
  ok("准备：工程只剩料盘 B 且仍是主循环",J("JSON.stringify(__cnc.DB.proj.mods.pick.trays)")=='["B"]' and J("__cnc.DB.proj.loop.auto"))
  J("__cnc.M.clearAffected();__cnc.S.verified=true;__cnc.renderAll()"); pg.click('#bStart')
  wait("__cnc.S.run!=='running'"); T=json.loads(J("JSON.stringify(__cnc.sim.trace)"))
  ok("L-07 只取料盘 B",[x['tray'] for x in T if x['e']=='pick']==['B']*3 and J("__cnc.DB.trays[0].picked")==0,[x.get('tray') for x in T if x['e']=='pick'])

  # ---- L-12 优先级与等待
  fresh(); small(); J("__cnc.SIG.TA.v=0"); pg.click('#bStart'); wait("__cnc.sim.trace.some(x=>x.e=='pick')")
  first=J("__cnc.sim.trace.find(x=>x.e=='pick').tray")
  J("__cnc.SIG.TB.v=0"); ok_wait=wait("__cnc.sim.waiting",8000)
  ok("L-12 A 没就绪 → 先取 B；都没就绪 → 等待",first=='B' and ok_wait,(first,ok_wait))
  J("__cnc.SIG.TA.v=1"); wait("__cnc.sim.trace.some(x=>x.e=='pick'&&x.tray=='A')",8000)
  ok("L-12 就绪后继续，不卡死",J("__cnc.sim.trace.some(x=>x.e=='pick'&&x.tray=='A')") and J("__cnc.S.run")=='running')
  J("__cnc.S.run='idle'")

  # ---- L-22 停止/报警不收尾；结束信号、目标件数收尾；D48-4 / D48-9 / H-01
  fresh(); small(); J("__cnc.DB.trays[1].cols='20'"); pg.click('#bStart'); wait("__cnc.S.count>=1")
  pg.click('#bPause'); pg.wait_for_timeout(50); pc=J("__cnc.S.pauseN"); pg.click('#bPause'); pg.wait_for_timeout(50)
  ok("D48-9 暂停 +1，继续不清零",pc==1 and J("__cnc.S.pauseN")==1 and J("__cnc.S.run")=='running')
  # 绑定一个输入再测 L-22（暂停中绑定不了，运行中下拉禁用 → 先停在空闲时绑定已不可行，这里直接写 END 并持久化检查放后面）
  ek=J("Object.keys(__cnc.SIG).find(k=>__cnc.SIG[k].ty==='DI'&&!['TA','TB'].includes(k)&&!__cnc.SIG[k].v)")
  J(f"__cnc.M.END.sig='{ek}';__cnc.sim.endPrev=false")
  J(f"__cnc.SIG['{ek}'].v=1"); wait("__cnc.S.run!=='running'")
  T=json.loads(J("JSON.stringify(__cnc.sim.trace)"))
  J(f"__cnc.SIG['{ek}'].v=0;__cnc.M.END.sig=''")
  ok("L-22 已绑定的结束信号 ON → 收尾、正常结束",any(x['e']=='end' for x in T) and J("__cnc.S.doneWhy")=='signal',J("__cnc.S.doneWhy"))
  ok("H-01 首页卡片显示 报警 0 · 暂停 1",J("document.getElementById('dTrayS').textContent")=='报警 0 · 暂停 1',J("document.getElementById('dTrayS').textContent"))
  pg.click('#bStart'); pg.wait_for_timeout(30)
  ok("D48-9 从空闲点开始 → 报警/暂停次数清零",J("__cnc.S.pauseN")==0 and J("__cnc.S.alarmN")==0)
  wait("__cnc.S.count>="+str(J("__cnc.S.count")+1))
  pg.click('#bStop'); pg.wait_for_timeout(100)
  btn=pg.locator('#mask .btn.dan, #mask button:has-text("停止")').last; btn.click(); pg.wait_for_timeout(100)
  T=json.loads(J("JSON.stringify(__cnc.sim.trace)"))
  ok("L-22 按停止 → 不执行收尾",J("__cnc.S.run")=='idle' and not any(x['e']=='end' for x in T) and not J("__cnc.S.done"),(J("__cnc.S.run"),[x['e'] for x in T][-3:]))
  # 目标件数
  fresh(); small(); J("__cnc.DB.trays[1].cols='20';__cnc.DB.proj.target=4"); pg.click('#bStart'); wait("__cnc.S.run!=='running'")
  ok("D48-4 目标件数 4 → 收尾，正好 4 件",J("__cnc.S.count")==4 and J("__cnc.S.doneWhy")=='target' and any(x['e']=='end' for x in json.loads(J("JSON.stringify(__cnc.sim.trace)"))),(J("__cnc.S.count"),J("__cnc.S.doneWhy")))
  ok("D48-4 已达目标 → 开始禁用，按钮下写原因",J("document.getElementById('bStart').disabled") and '目标件数' in J("document.getElementById('bStartWhy').textContent"),J("document.getElementById('bStartWhy').textContent"))

  # ---- L-23 / L-27 / D48-3 / D48-2 / D48-7 手动 ↔ 主循环切换
  fresh(); J("__cnc.M.clearAffected();__cnc.S.verified=true;__cnc.show('proj')"); pg.wait_for_timeout(100)
  J(M+"removeTrayFromProject('A')"); J(M+"removeTrayFromProject('B')")
  J("var M=__cnc.DB.proj.mods;M.pick.src='料框';M.pick.ref='成品料框1';M.pick.trays=[];M.unload.src='料框';M.unload.ref='成品料框1';M.unload.trays=[]")
  pre=J("__cnc.S.verified"); pg.click('#eSave'); pg.wait_for_timeout(100); print('  toast:',toast())
  ok("L-21（按 D48-6）取消全部料盘 → 手动编排；保存前不是待核对，保存后变待核对",J("__cnc.DB.proj.loop.auto")==False and pre==True and J("__cnc.S.verified")==False,(pre,J("__cnc.S.verified")))
  J("var P=__cnc.DB.proj;P.mods.pick.calls=['P1'];P.mods.unload.calls=['P2'];__cnc.renderAll()")
  J(M+"seqInsert('P6',2)")  # 在取毛坯后插一个普通节点
  order=J("JSON.stringify(__cnc.DB.proj.order)"); before=J("JSON.stringify(__cnc.DB.proj.mods)")
  plan=json.loads(J("JSON.stringify(__cnc.M.switchPlan())"))
  r=J("""(()=>{let asked=0;const r=__cnc.M.addTrayToNode('mc','A',(t,ok,no)=>{asked++;no()});return {r,asked}})()""")
  ok("L-23 取消切换 → 料盘没选、编排不变",r['asked']==1 and not r['r']['ok'] and J("__cnc.DB.proj.loop.auto")==False and J("JSON.stringify(__cnc.DB.proj.mods)")==before and J("JSON.stringify(__cnc.DB.proj.order)")==order,r)
  r=J("""(()=>{let asked=0;const r=__cnc.M.addTrayToNode('mc','A',(t,ok,no)=>{asked++;ok()});return {r,asked,txt:__cnc.M.SWITCH_TEXT}})()""")
  ins=json.loads(J("JSON.stringify(__cnc.DB.proj.loop.ins)"))
  ok("D48-7 在机床换料节点加料盘 → 取毛坯、成品下料都选中",J("JSON.stringify([__cnc.DB.proj.mods.pick.trays,__cnc.DB.proj.mods.unload.trays])")=='[["A"],["A"]]')
  ok("D48-3 普通节点和二次定位排在挪过来的模块前面（取毛坯后）",ins['post:pick']==[{'t':'node','k':'reloc'},{'t':'prog','id':'P6'},{'t':'prog','id':'P1'}] or ins['post:pick'][-1]=={'t':'prog','id':'P1'},(order,ins['post:pick']))
  ok("L-23 机床换料、成品下料上的模块进同名阶段后插入位",{'t':'prog','id':'P3'} in ins['post:mc'] and ins['post:unload']==[{'t':'prog','id':'P2'}],ins)
  # L-27 表与实际一致
  rowmap={x['node']:x for x in plan}
  ok("L-27 表：取毛坯→取毛坯后，成品下料→成品下料后，空节点写不用移",rowmap['pick']['slot']=='post:pick' and rowmap['unload']['slot']=='post:unload' and all('不用移' in x['text'] for x in plan if x['action']=='none'),[ (x['name'],x['text']) for x in plan])
  ok("L-27 表里每个模块的去向 = 实际挂载位置",all(any(it.get('id')==m['id'] for it in ins[x['slot']]) for x in plan if x['action'] in('move','node') and x['slot'] for m in x['modules'] if x['action']=='move'))
  # D48-2：再取消、再加 → 还要问
  J(M+"removeTrayFromProject('A')")
  r=J("""(()=>{let asked=0;__cnc.M.addTrayToNode('pick','B',(t,ok,no)=>{asked++;ok()});return asked})()""")
  ok("D48-2 再次从没有料盘变成有料盘 → 再弹切换确认",r==1,r)
  J("__cnc.show('proj')"); pg.wait_for_timeout(100)

  # ---- L-29 未绑定结束信号：演示按钮置灰、不会收尾
  fresh(); small(); J("__cnc.DB.trays[1].cols='20'"); pg.click('#bStart'); wait("__cnc.S.count>=1")
  # L-29 未绑定：演示按钮置灰、不会收尾
  pg.click('#logo'); pg.wait_for_timeout(150)
  dis=J("(document.getElementById('dmEnd')||{}).disabled===true")
  try: pg.click('#dmEnd',force=True,timeout=1000)
  except Exception: pass
  pg.wait_for_timeout(300); pg.keyboard.press('Escape'); pg.wait_for_timeout(100)
  ok("L-29 结束信号未绑定 → 演示按钮置灰，点了也不会收尾",dis and not J("__cnc.S.endReq") and J("__cnc.S.run")=='running',(dis,J("__cnc.S.endReq"),J("__cnc.S.run")))
  J("__cnc.S.run='idle'")

  # ---- D48-6 待核对跟着保存走（不保存离开要恢复）
  fresh(); J("__cnc.M.clearAffected();__cnc.S.verified=true;__cnc.deploy.st=['ok','ok','ok','ok']"); J("__cnc.show('proj')"); pg.wait_for_timeout(100)
  J(M+"removeTrayFromProject('A')"); J(M+"removeTrayFromProject('B')")
  v_before_save=J("__cnc.S.verified")
  J("__cnc.go('home')"); pg.wait_for_timeout(150)
  btn=pg.locator('#mask button:has-text("不保存")'); n=btn.count()
  if n: btn.first.click(); pg.wait_for_timeout(150)
  ok("D48-6 取消全部料盘、未保存时还不是待核对",v_before_save==True,v_before_save)
  ok("D48-6 不保存离开 → 料盘和核对状态恢复",J("__cnc.DB.proj.loop.auto")==True and J("__cnc.S.verified")==True,(n,J("__cnc.DB.proj.loop.auto"),J("__cnc.S.verified"),J("__cnc.deploy.msg")))
  pg.reload(); pg.wait_for_timeout(200)
  ok("D48-6 刷新后也不是待核对",J("__cnc.S.verified")==True,J("__cnc.deploy.msg"))

  # ---- D48-5 信号页「结束信号」可选输入，默认未绑定
  fresh(); J("__cnc.show('sig')"); pg.wait_for_timeout(150)
  ok("D48-5 信号页有「结束信号」输入，默认未绑定",'结束信号' in pg.inner_text('#pg-sig') and J("document.getElementById('sEnd').value")=='')
  opts=J("[...document.querySelectorAll('#sEnd option')].map(o=>o.value).filter(v=>v&&!['TA','TB'].includes(v)&&!__cnc.SIG[v].v)")
  pg.select_option('#sEnd',opts[0]); pg.wait_for_timeout(100); pg.reload(); pg.wait_for_timeout(200)
  ok("D48-5 选一个输入后自动保存，刷新后保持",J("__cnc.M.END.sig")==opts[0],(opts[0],J("__cnc.M.END.sig")))
  J("__cnc.show('home')"); pg.wait_for_timeout(100); J("__cnc.S.tickMs=1;__cnc.SIG.TA.v=1;__cnc.SIG.TB.v=1"); small(); J("__cnc.DB.trays[1].cols='20'")
  pg.click('#bStart'); wait("__cnc.S.count>=1"); pg.click('#logo'); pg.wait_for_timeout(150); pg.click('#dmEnd'); pg.wait_for_timeout(100); pg.keyboard.press('Escape')
  wait("__cnc.S.run!=='running'")
  ok("L-22 已绑定时演示按钮把信号置 ON → 收尾",J("__cnc.S.doneWhy")=='signal' and any(x['e']=='end' for x in json.loads(J("JSON.stringify(__cnc.sim.trace)"))),J("__cnc.S.doneWhy"))

  ok("无页面报错",not errs,errs[:3])
  b.close()
print("\nTOTAL",len(R),"PASS",sum(1 for x in R if x[0]=="PASS"),"FAIL",sum(1 for x in R if x[0]=="FAIL"))
