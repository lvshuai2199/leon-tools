import json
from playwright.sync_api import sync_playwright
U="file:///workspace/cnc-v3/tmp/cnc-prototype.fix1004.html"
R={}
class A:
  def __init__(s,pg): s.pg=pg; s.n=0; s.log=[]
  def c(s,sel,note=''):
    s.pg.locator(sel).first.click(); s.n+=1; s.log.append('点'+(note or sel)); s.pg.wait_for_timeout(150)
  def f(s,sel,v,note=''):
    l=s.pg.locator(sel).first; l.fill(str(v)); l.dispatch_event('change'); s.n+=1; s.log.append('填'+(note or sel)+'='+str(v)); s.pg.wait_for_timeout(120)
  def nav(s,page):
    s.pg.locator('[data-p="%s"]'%page).first.click()
    s.n+=1; s.log.append('进'+page); s.pg.wait_for_timeout(200)
def st(pg):
  return pg.evaluate("""()=>{var c=__cnc;var m=document.getElementById('modal');return {verified:c.S.verified,startWhy:c.M.startWhy(),affected:(c.M.affectedText&&c.M.affectedText())||'',dirty:JSON.stringify(c.dirty),
   yellow:[...document.querySelectorAll('#pg-proj .stage,#pg-proj .pill,#pg-proj .node')].filter(e=>/aff|warn|yel/.test(e.className)).map(e=>e.id),
   toast:(document.querySelector('.toast')||{}).innerText||'',modal:m&&m.offsetParent?m.innerText.replace(/\\s+/g,' ').slice(0,160):''}}""")
def fresh(b):
  pg=b.new_page(viewport={"width":1280,"height":800}); pg.set_default_timeout(3000); pg.on("dialog",lambda d:d.accept())
  pg.goto(U); pg.evaluate("localStorage.clear()"); pg.reload(); pg.wait_for_timeout(300); pg.evaluate("__cnc.S.fast=true"); return pg

def leave_check(pg,a,page_away='home'):
  # 尝试不保存离开，看是否弹窗、能否恢复
  pg.locator('[data-p="%s"]'%page_away).first.click(); pg.wait_for_timeout(200)
  m=st(pg)['modal']; btns=pg.evaluate("[...document.querySelectorAll('#modal button')].filter(e=>e.offsetParent).map(e=>e.innerText.trim())")
  return m,btns
def click_modal(pg,txt):
  pg.locator('#modal button',has_text=txt).first.click(); pg.wait_for_timeout(250)
def run(name,fn,b):
  pg=fresh(b); a=A(pg); out={}
  errs=[]; pg.on('pageerror',lambda e:errs.append(str(e)))
  try: fn(pg,a,out)
  except Exception as e: out['EXC']=repr(e)[:300]
  out['steps']=a.n; out['log']=a.log; out['pageerrors']=errs
  R[name]=out; print('\n=====',name); print(json.dumps(out,ensure_ascii=False,indent=1)); pg.close()

def s1(pg,a,o):  # 料盘换规格
  a.nav('tray'); a.c('#tEdit','编辑'); a.f('#ts-rows',4,'行'); a.f('#ts-cols',6,'列'); a.f('#ts-rp','40.0','行距'); a.f('#ts-layers',2,'层')
  o['before_save']=st(pg); o['tCnt_edit']=pg.inner_text('#tCnt')
  m,bt=leave_check(pg,a); o['leave_modal']=m; o['leave_btns']=bt
  if bt:
    click_modal(pg,'不保存'); o['after_nosave']=pg.evaluate("(t=>[t.rows,t.cols,t.rp,t.layers].join('/'))(__cnc.DB.trays[0])")
  a.nav('tray'); o['edit_mode_kept_after_leave']=pg.evaluate("!document.getElementById('pg-tray').classList.contains('ro')")
  if not o['edit_mode_kept_after_leave']: a.c('#tEdit','编辑')
  a.f('#ts-rows',4,'行'); a.f('#ts-cols',6,'列'); a.f('#ts-rp','40.0','行距'); a.f('#ts-layers',2,'层'); a.c('#tSave','保存')
  o['modal_after_save']=st(pg)['modal']
  if o['modal_after_save']:
    o['modal_btns']=pg.evaluate("[...document.querySelectorAll('#modal button')].filter(e=>e.offsetParent).map(e=>e.innerText.trim())")
    click_modal(pg,'继续保存'); a.n+=1; a.log.append('点继续保存')
  o['after_save']=st(pg); o['tray']=pg.evaluate("(t=>[t.rows,t.cols,t.rp,t.layers,t.ver,t.picked].join('/'))(__cnc.DB.trays[0])")
  o['tCnt']=pg.inner_text('#tCnt'); o['total']=pg.evaluate("__cnc.M.trayTotal(__cnc.DB.trays[0])")
  a.nav('home'); o['home']=st(pg); o['home_ver_btn']=pg.evaluate("(e=>e&&e.offsetParent?e.innerText:'')(document.getElementById('hVer'))")
  o['bStart_disabled']=pg.evaluate("document.getElementById('bStart').disabled")
def s2(pg,a,o):  # 改目标件数
  a.nav('proj'); 
  if not pg.locator('#cfg-target').is_visible(): a.c('#pg-proj button:has-text("工程配置")','工程配置')
  a.f('#cfg-target',600,'目标件数')
  o['before_save']=st(pg); m,bt=leave_check(pg,a); o['leave_modal']=m; o['leave_btns']=bt
  if bt: click_modal(pg,'不保存'); o['after_nosave']=pg.evaluate("__cnc.DB.proj.target")
  a.nav('proj')
  if not pg.locator('#cfg-target').is_visible(): a.c('#pg-proj button:has-text("工程配置")','工程配置')
  a.f('#cfg-target',600,'目标件数'); a.c('#eSave','保存'); o['modal_after_save']=st(pg)['modal']
  o['after']=st(pg); o['target']=pg.evaluate("__cnc.DB.proj.target")
  a.f('#cfg-target',3,'目标件数(小于已完成?)'); o['small_target_hint']=st(pg)['toast']+' | '+pg.evaluate("(e=>e.closest('.cfgrow,.row,div').innerText.replace(/\\s+/g,' ').slice(0,120))(document.getElementById('cfg-target'))")
def s3(pg,a,o):  # 改取放点位
  a.nav('prog'); a.c('#rList .pi >> nth=0','程序 上料-取毛坯')
  # 找带点位的步骤
  rows=pg.locator('#pg-prog .step, #pg-prog .st, #pg-prog [data-si]')
  o['step_rows']=rows.count()
  rows.nth(2).click(); a.n+=1; a.log.append('点步骤3'); pg.wait_for_timeout(150)
  o['pt']=pg.evaluate("(e=>e?e.value:'')(document.getElementById('sp-pt'))")
  o['shared_by']=pg.evaluate("(pt=>__cnc.DB.progs.filter(p=>p.steps.some(s=>s.pt===pt)).map(p=>p.n))(document.getElementById('sp-pt').value)")
  before=pg.evaluate("JSON.stringify(__cnc.DB.points[document.getElementById('sp-pt').value])")
  a.c('#sp-rec','记录当前位置'); o['after_rec']=st(pg)
  m,bt=leave_check(pg,a); o['leave_modal']=m; o['leave_btns']=bt
  if bt: click_modal(pg,'不保存'); o['point_restored']=pg.evaluate("JSON.stringify(__cnc.DB.points['%s'])"%o['pt'])==before
  a.nav('prog'); a.c('#rList .pi >> nth=0','程序'); pg.locator('#pg-prog .step, #pg-prog .st, #pg-prog [data-si]').nth(2).click(); a.n+=1
  a.c('#sp-rec','记录当前位置'); a.c('#rSave','保存'); o['modal_after_save']=st(pg)['modal']
  if o['modal_after_save']: o['modal_btns']=pg.evaluate("[...document.querySelectorAll('#modal button')].filter(e=>e.offsetParent).map(e=>e.innerText.trim())")
  o['after']=st(pg); o['vers']=pg.evaluate("__cnc.DB.progs.map(p=>p.id+'v'+p.ver).join(' ')")
  a.nav('proj'); o['proj_view']=st(pg)
def s4(pg,a,o):  # 插入/去掉模块
  a.nav('proj'); o['order0']=pg.evaluate("JSON.stringify(__cnc.DB.proj.loop||__cnc.DB.proj.order)")[:300]
  a.c('#pg-proj .sadd >> nth=2','插入位(取毛坯后)'); o['after_slot']=st(pg)
  o['pk_enabled']=pg.evaluate("[...document.querySelectorAll('#pg-proj .pk-add')].filter(e=>!e.className.includes('dis')).length")
  o['pk_rows']=pg.evaluate("[...document.querySelectorAll('#pg-proj .pk-add')].map(e=>(e.closest('div,li').innerText||'').replace(/\\s+/g,' ').slice(0,30))")
  pg.locator('#pg-proj .pk-add').nth(5).click(); a.n+=1; a.log.append('点加入(第6个程序)'); pg.wait_for_timeout(200)
  o['after_insert']=st(pg); o['order1']=pg.evaluate("JSON.stringify(__cnc.DB.proj.loop||__cnc.DB.proj.order)")[:400]
  o['nodes']=pg.evaluate("[...document.querySelectorAll('#pg-proj .stage,#pg-proj .pill')].map(e=>e.innerText.replace(/\\s+/g,' ').slice(0,18))")
  a.c('#n-mc','机床换料'); a.c('#mc-blow','吹气开关'); o['blow']=pg.evaluate("__cnc.DB.proj.mods.mc.blow")
  a.c('#eSave','保存'); o['modal_after_save']=st(pg)['modal']; o['after_save']=st(pg)
  # 去掉刚插入的
  pg.locator('#pg-proj .pill',has_text='设备前端安全点位').first.click(); a.n+=1; a.log.append('点插入的程序块'); pg.wait_for_timeout(200)
  o['props_btns']=pg.evaluate("[...document.querySelectorAll('#eProps button')].filter(e=>e.offsetParent).map(e=>(e.id||'')+':'+(e.innerText||e.title||e.getAttribute('aria-label')||'').trim().slice(0,12))")
  pg.evaluate("[...document.querySelectorAll('#eProps button')].find(e=>e.offsetParent&&/移除/.test((e.innerText||'')+(e.title||'')+(e.getAttribute('aria-label')||''))).click()"); a.n+=1; a.log.append('点移除'); pg.wait_for_timeout(200); o['after_remove_modal']=st(pg)['modal']
  if o['after_remove_modal']: o['rm_btns']=pg.evaluate("[...document.querySelectorAll('#modal button')].filter(e=>e.offsetParent).map(e=>e.innerText.trim())")
  o['order2']=pg.evaluate("JSON.stringify(__cnc.DB.proj.loop.ins['post:pick'])"); o['after_remove']=st(pg)
  a.c('#eSave','保存'); o['after_remove_save']=st(pg)
  o['remove_ui']=pg.evaluate("[...document.querySelectorAll('#pg-proj button')].filter(e=>e.offsetParent&&/删除|移除|去掉|×/.test(e.innerText+e.title)).map(e=>(e.id||e.className)+':'+(e.innerText||e.title).trim().slice(0,10))")
def s5(pg,a,o):  # 程序升级后工程跟随
  a.nav('prog'); pg.locator('#rList .pi',has_text='设备前端安全点位').first.click(); a.n+=1; a.log.append('点程序 设备前端安全点位'); pg.wait_for_timeout(150); 
  pid=pg.evaluate("__cnc.R&&__cnc.R.sel||''"); o['sel']=pid
  o['list']=pg.evaluate("[...document.querySelectorAll('#rList .pi')].map(e=>e.innerText.replace(/\\s+/g,' ').slice(0,30))")
  rows=pg.locator('#pg-prog .step, #pg-prog .st, #pg-prog [data-si]')
  for i in range(rows.count()):
    rows.nth(i).click(); pg.wait_for_timeout(120)
    if pg.locator('#sp-spd').count() and pg.locator('#sp-spd').is_visible(): a.n+=1; a.log.append('点第%d步'%(i+1)); break
  a.f('#sp-spd',25,'速度'); a.c('#rSave','保存')
  o['modal_after_save']=st(pg)['modal']
  if o['modal_after_save']: o['modal_btns']=pg.evaluate("[...document.querySelectorAll('#modal button')].filter(e=>e.offsetParent).map(e=>e.innerText.trim())")
  o['vers']=pg.evaluate("__cnc.DB.progs.map(p=>p.id+'v'+p.ver).join(' ')")
  a.nav('proj'); o['proj']=st(pg)
  a.nav('home'); o['home']=st(pg); o['bStart_disabled']=pg.evaluate("document.getElementById('bStart').disabled")
  a.nav('proj'); a.c('#eDeploy','下发到机器人'); pg.wait_for_timeout(1500)
  if st(pg)['modal']: o['deploy_modal']=st(pg)['modal']; click_modal(pg,'下发')
  pg.wait_for_timeout(1500); o['after_deploy']=st(pg)
def s6(pg,a,o):  # 复制工程
  a.nav('proj'); o['options']=pg.evaluate("[...document.querySelectorAll('#eProj option')].map(e=>e.text)")
  pg.select_option('#eProj',index=1); a.n+=1; pg.wait_for_timeout(200); o['toast']=st(pg)['toast']; o['proj_after']=pg.evaluate("document.getElementById('eProj').value")
  a.nav('tray'); a.c('#tMore','更多'); o['tray_menu']=pg.evaluate("[...document.querySelectorAll('[id^=tm]')].filter(e=>e.offsetParent).map(e=>e.innerText.trim())")
  o['prog_copy']=pg.evaluate("[...document.querySelectorAll('#pg-prog button')].filter(e=>/复制|另存/.test(e.innerText)).map(e=>e.innerText)")
  o['export']=pg.evaluate("[...document.querySelectorAll('button')].filter(e=>/导出|导入|备份|历史|版本记录|回退|撤销/.test(e.innerText)).map(e=>e.id+':'+e.innerText.trim().slice(0,12))")
with sync_playwright() as p:
  b=p.chromium.launch()
  def s0(pg,a,o):
    a.nav('home'); o['home_btns']=pg.evaluate("[...document.querySelectorAll('#pg-home button')].filter(e=>e.offsetParent).map(e=>(e.id||'')+':'+e.innerText.trim().slice(0,10))")
    a.c('#bStart','开始'); pg.wait_for_timeout(500); o['after_start']=st(pg)
    if o['after_start']['modal']: o['start_modal_btns']=pg.evaluate("[...document.querySelectorAll('#modal button')].filter(e=>e.offsetParent).map(e=>e.innerText.trim())")
    o['running']=pg.evaluate("JSON.stringify(Object.keys(__cnc.sim||{}))")[:200]
  for n,fn in [('S0示例工程直接开始',s0),('S1料盘换规格',s1),('S2改目标件数',s2),('S3改取放点位',s3),('S4插入去掉模块',s4),('S5程序升级',s5),('S6复制工程',s6)]: run(n,fn,b)
json.dump(R,open('scen.json','w'),ensure_ascii=False,indent=1)
