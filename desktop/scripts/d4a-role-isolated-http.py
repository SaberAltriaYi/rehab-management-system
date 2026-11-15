#!/usr/bin/env python3
"""Source-pinned D4-A HTTP dual-tenant role security acceptance (SYNTHETIC ONLY).

Runs fresh MySQL 8.4, Redis 7.4 and the 79cf161e4b candidate on a random
Docker --internal bridge, with tmpfs data, no bind mounts, and no published
ports. Only synthetic test rows are created. Never points a write at live Core.
All secrets are generated per run and excluded from logs/evidence.
"""
import hashlib
import json
import os
from pathlib import Path
import secrets
import subprocess
import sys
import time
import uuid

ROOT = Path(__file__).resolve().parents[2]
EVIDENCE = ROOT.parent / 'validation-evidence' / 'qa-20261116-d4a-http-e2e'
BOOTSTRAP = ROOT / 'desktop/runtime/1.0.0/sql/desktop-bootstrap.sql'
V3 = ROOT / 'sql/mysql/rehab-saas-provisioning-v3.sql'
V4 = ROOT / 'sql/mysql/rehab-saas-entitlement-sync-v4.sql'
IMAGE = 'rehab-d4a-synthetic:79cf161e4b'
EXPECTED_IMAGE = 'sha256:cefbe6846abd086e88c0f0ad2889b037ee5137c142e813f3b2c1dd01229553c7'
EXPECTED_JAR = '0c5e46d00ec844d75ccb3ea5132b43442899bf2d5c99fe2ab27b03cbd4cbeebe'
SOURCE_SHA = '79cf161e4b6e8e8eab5e8567fa353f2187286f09'
LIVE_CONTAINERS = ('rehab-studio-internal-server-1', 'rehab-studio-internal-mysql-1')
DB = 'ruoyi-vue-pro'
tag = secrets.token_hex(5)
net = 'rehab-d4a-httpe2e-' + tag
mysql = net + '-mysql'
redis = net + '-redis'
server = net + '-server'
rootpass = secrets.token_urlsafe(24)
dbpass = secrets.token_urlsafe(24)
redispass = secrets.token_urlsafe(24)
machine_token = secrets.token_urlsafe(36)
adminpass = secrets.token_hex(8)
staffpass = secrets.token_hex(8)
containers = []
network_created = False
checks = []


def call(args, *, input_bytes=None, check=True, timeout=40):
    try:
        p = subprocess.run(args, input=input_bytes, stdout=subprocess.PIPE,
                           stderr=subprocess.PIPE, timeout=timeout)
    except subprocess.TimeoutExpired as exc:
        raise RuntimeError('ISOLATED_COMMAND_TIMEOUT') from exc
    if check and p.returncode:
        raise RuntimeError('ISOLATED_COMMAND_FAILED_' + args[0])
    return p


def docker(*args, **kwargs):
    return call(['docker', *args], **kwargs)


def check(name, ok, note=''):
    checks.append({'name': name, 'passed': bool(ok), 'note': str(note)[:150]})
    print(('PASS ' if ok else 'FAIL ') + name + (': ' + str(note)[:100] if note else ''), flush=True)
    if not ok:
        raise RuntimeError('GATE_FAILED_' + name)


def sql(statement):
    p = docker('exec', '-e', 'MYSQL_PWD=' + rootpass, mysql, 'mysql',
               '-uroot', '--batch', '--skip-column-names', '--raw', DB,
               '--execute', statement, timeout=35)
    return p.stdout.decode().strip()


def import_sql(path):
    with path.open('rb') as f:
        p = subprocess.run(['docker','exec','-i','-e','MYSQL_PWD='+rootpass,
                            mysql,'mysql','-uroot','--binary-mode',DB],
                           stdin=f,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=110)
    if p.returncode:
        raise RuntimeError('SYNTHETIC_SCHEMA_IMPORT_FAILED_'+path.name)


def request(path, *, method='GET', payload=None, tenant=None, bearer=None, token=None):
    args = ['docker','exec','-i',server,'curl','--noproxy','*','--silent',
            '--show-error','--max-time','17','--request',method,
            '--header','Content-Type: application/json',
            '--output','/dev/stdout','--write-out','\n%{http_code}',
            '--url','http://127.0.0.1:48080'+path]
    if tenant is not None:
        args += ['--header','tenant-id: '+str(int(tenant))]
    if bearer is not None:
        args += ['--header','Authorization: Bearer '+bearer]
    if token is not None:
        args += ['--header','X-Rehab-Provisioning-Token: '+token]
    data = None
    if method in ('POST','PUT','PATCH'):
        args += ['--data-binary','@-']
        data = json.dumps({} if payload is None else payload,ensure_ascii=False).encode()
    response = call(args,input_bytes=data,timeout=30)
    raw,_,status = response.stdout.rpartition(b'\n')
    try:
        body = json.loads(raw)
    except Exception:
        body = None
    return int(status),body


def detail(status,body):
    return 'HTTP='+str(status)+' app_code='+str(body.get('code') if isinstance(body,dict) else None)


def success(status,body):
    return status==200 and isinstance(body,dict) and body.get('code')==0


def denied(status,body):
    return status in (401,403) or (isinstance(body,dict) and body.get('code') in (401,403))


def business_denied(status,body):
    return isinstance(body,dict) and body.get('code') not in (None,0) and body.get('data') is None


def wait_mysql():
    for _ in range(85):
        p = docker('exec','-e','MYSQL_PWD='+rootpass,mysql,'mysql','-uroot',
                   '--batch','--skip-column-names','--execute','SELECT 1',
                   check=False,timeout=6)
        if p.returncode==0 and p.stdout.strip()==b'1': return
        time.sleep(1.2)
    raise RuntimeError('ISOLATED_MYSQL_NOT_READY')


def wait_http():
    for i in range(135):
        p = docker('exec',server,'curl','--noproxy','*','--silent','--max-time','5',
                   '--output','/dev/null','--write-out','%{http_code}',
                   'http://127.0.0.1:48080/actuator/health',check=False,timeout=8)
        if p.returncode==0 and p.stdout.strip()==b'200':return True
        if i%15==0:print('INFO isolated_d4a_core_booting_probe='+str(i),flush=True)
        time.sleep(2)
    return False


def provision(org, username, plan_id, package_id):
    req={'idempotencyKey':str(uuid.uuid4()),'tenantUuid':str(uuid.uuid4()),
         'organizationName':'SYNTHETIC_D4A_'+org+'_'+tag,
         'contactName':'Synthetic','contactMobile':'',
         'planId':int(plan_id),'packageId':int(package_id),
         'expiresAt':int(time.time()*1000)+86400*1000*300,
         'adminUsername':username,'adminPassword':adminpass,'seatLimit':20}
    st,b=request('/admin-api/system/saas/internal/provisioning',method='POST',
                 payload=req,token=machine_token)
    d=b.get('data') if isinstance(b,dict) else None
    check('tenant-'+org+'-provision-active',success(st,b) and isinstance(d,dict)
          and d.get('status')=='ACTIVE' and isinstance(d.get('tenant_id'),int),detail(st,b))
    return d['tenant_id']


def login(username,password,tenant, label):
    st,b=request('/admin-api/system/auth/login',method='POST',tenant=tenant,
                 payload={'username':username,'password':password})
    d=b.get('data') if isinstance(b,dict) else None
    access=d.get('accessToken') if isinstance(d,dict) else None
    check('login-'+label,success(st,b) and isinstance(access,str) and len(access)>12,
          detail(st,b))
    return access,d.get('userId')


def create_user(admin_token,tenant,username,label):
    st,b=request('/admin-api/system/user/create',method='POST',tenant=tenant,
                 bearer=admin_token,payload={'username':username,'nickname':'Synthetic '+label,
                                            'password':staffpass})
    u=b.get('data') if isinstance(b,dict) else None
    check('tenant-'+label+'-user-create',success(st,b) and isinstance(u,int),detail(st,b))
    return u


def page_ids(path,tenant,access):
    st,b=request(path,tenant=tenant,bearer=access)
    data=b.get('data') if isinstance(b,dict) else None
    ids={x.get('id') for x in data.get('list',[])} if isinstance(data,dict) else set()
    return st,b,ids


def stage_seed_patients(a,b,therapist_a):
    # The only business rows are hard-coded fictional IDs and names. They are
    # inserted into the random tmpfs mysql container, NEVER a host database.
    sql("INSERT INTO rehab_patient(id,patient_no,name,current_therapist_user_id,tenant_id) VALUES "
        "(991101,'D4A_A_ASSIGNED','SYNTH_A_ASSIGNED',"+str(therapist_a)+","+str(a)+"),"
        "(991102,'D4A_A_OTHER','SYNTH_A_OTHER',NULL,"+str(a)+"),"
        "(992101,'D4A_B_ONLY','SYNTH_B_ONLY',NULL,"+str(b)+");")
    sql("INSERT INTO rehab_episode(id,episode_no,patient_id,tenant_id) VALUES "
        "(991201,'D4A_EP_A1',991101,"+str(a)+"),"
        "(991202,'D4A_EP_A2',991102,"+str(a)+"),"
        "(992201,'D4A_EP_B1',992101,"+str(b)+");")
    sql("INSERT INTO rehab_assessment_record(id,assessment_no,patient_id,episode_id,assessment_type,assessment_date,tenant_id) VALUES "
        "(991301,'D4A_ASS_A1',991101,991201,'initial','2026-11-16',"+str(a)+"),"
        "(991302,'D4A_ASS_A2',991102,991202,'initial','2026-11-16',"+str(a)+"),"
        "(992301,'D4A_ASS_B1',992101,992201,'initial','2026-11-16',"+str(b)+");")
    sql("INSERT INTO rehab_report(id,report_no,patient_id,episode_id,assessment_id,report_type,report_status,tenant_id) VALUES "
        "(991401,'D4A_REPORT_A1',991101,991201,991301,'comprehensive','draft',"+str(a)+"),"
        "(991402,'D4A_REPORT_A2',991102,991202,991302,'comprehensive','draft',"+str(a)+"),"
        "(992401,'D4A_REPORT_B1',992101,992201,992301,'comprehensive','draft',"+str(b)+");")
    sql("INSERT INTO rehab_motion_assessment(id,patient_id,title,status,input_revision,analyzed_revision,review_status,tenant_id) VALUES "
        "(991501,991101,'SYNTH_MOTION_A1','PENDING_REVIEW',1,1,'in_review',"+str(a)+"),"
        "(991502,991102,'SYNTH_MOTION_A2','PENDING_REVIEW',1,1,'in_review',"+str(a)+"),"
        "(992501,992101,'SYNTH_MOTION_B1','PENDING_REVIEW',1,1,'in_review',"+str(b)+");")
    sql("INSERT INTO rehab_motion_score(id,assessment_id,family,test_code,scoring_scheme,"
        "final_score,final_status,analysis_revision,tenant_id) VALUES "
        "(991601,991501,'FMS','DEEP_SQUAT','FMS_0_3',2,'confirmed',1,"+str(a)+");")
    check('fictional-patient-episode-assessment-report-and-motion-rows',
          sql("SELECT CONCAT((SELECT COUNT(*) FROM rehab_patient WHERE id IN (991101,991102,992101)),':',"
              "(SELECT COUNT(*) FROM rehab_episode WHERE id IN (991201,991202,992201)),':',"
              "(SELECT COUNT(*) FROM rehab_report WHERE id IN (991401,991402,992401)),':',"
              "(SELECT COUNT(*) FROM rehab_motion_assessment WHERE id IN (991501,991502,992501)))")=='3:3:3:3')


def run():
    global network_created
    if len(sys.argv)!=2 or sys.argv[1]!='--synthetic-only':
        raise RuntimeError('EXPLICIT_--synthetic-only_REQUIRED')
    check('pinned-source-commit-in-history',call(
        ['git','merge-base','--is-ancestor',SOURCE_SHA,'HEAD'],check=False).returncode==0)
    check('built-jar-fixed',hashlib.sha256((ROOT/'yudao-server/target/yudao-server.jar').read_bytes()).hexdigest()==EXPECTED_JAR)
    check('candidate-image-exact',docker('image','inspect',IMAGE,'--format','{{.Id}}').stdout.decode().strip()==EXPECTED_IMAGE)
    check('sanitized-bootstrap-only',BOOTSTRAP.read_text().startswith('-- 康复管理系统 V1.0 桌面端脱敏初始化快照'))
    before={name:docker('inspect',name,'--format','{{.Image}}|{{.State.Running}}').stdout.decode().strip()
            for name in LIVE_CONTAINERS}
    check('production-running-before',all(x.endswith('|true') for x in before.values()))
    check('no-colliding-test-network',net not in docker('network','ls','--format','{{.Name}}').stdout.decode().splitlines())
    docker('network','create','--internal','--driver','bridge',net)
    network_created=True
    check('no-egress-internal-network',docker('network','inspect',net,'--format','{{.Internal}}').stdout.strip()==b'true')
    docker('run','-d','--name',mysql,'--network',net,'--restart','no',
           '--memory','1800m','--pids-limit','256','--tmpfs','/var/lib/mysql:rw,size=768m',
           '-e','MYSQL_ROOT_PASSWORD='+rootpass,'-e','MYSQL_DATABASE='+DB,
           '-e','MYSQL_USER=synthetic','-e','MYSQL_PASSWORD='+dbpass,'mysql:8.4.10')
    containers.append(mysql)
    docker('run','-d','--name',redis,'--network',net,'--restart','no',
           '--memory','256m','--pids-limit','128','--tmpfs','/data:rw,size=128m',
           'redis:7.4.10-alpine3.21','redis-server','--save','','--appendonly','no','--requirepass',redispass)
    containers.append(redis)
    wait_mysql()
    import_sql(BOOTSTRAP)
    import_sql(V3)
    import_sql(V4)
    check('schema-v036-v3-v4-isolated',sql("SELECT CONCAT(MAX(version),':',COUNT(*)) FROM internal_schema_history")=='036:36'
          and sql("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='saas_provisioning_requests' AND column_name='entitlement_revision'")=='1')
    check('zero-business-patients-before-fixtures',sql('SELECT COUNT(*) FROM rehab_patient')=='0')
    menu_count=int(sql("SELECT COUNT(*) FROM system_menu WHERE status=0 AND deleted=0"))
    check('catalog-274-enabled-menus',menu_count==274,'count='+str(menu_count))
    package_ids=sql("SELECT CAST(JSON_ARRAYAGG(id) AS CHAR) FROM system_menu WHERE status=0 AND deleted=0")
    check('catalog-generated-from-sanitized-bootstrap',len(json.loads(package_ids))==274)
    sql("INSERT INTO system_tenant_package(id,name,status,remark,menu_ids,creator,updater,deleted) VALUES "
        "(990113,'SYNTHETIC_D4A_FULLCATALOG',0,'DISPOSABLE_ONLY','"+package_ids+"','synthetic','synthetic',b'0')")
    sql("INSERT INTO saas_plans(name,max_users,max_patients,motion_quota,storage_quota,status) "
        "VALUES ('SYNTHETIC_D4A_20_400_2000_5G',20,400,2000,5368709120,1)")
    plan_id=sql("SELECT id FROM saas_plans WHERE name='SYNTHETIC_D4A_20_400_2000_5G'")
    docker('run','-d','--name',server,'--network',net,'--restart','no',
           '--memory','2300m','--pids-limit','512',
           '--tmpfs','/app/data/rehab:rw,size=128m,mode=1777',
           '--tmpfs','/app/logs:rw,size=64m,mode=1777',
           '-e','SPRING_PROFILES_ACTIVE=internal',
           '-e','DB_HOST='+mysql,'-e','DB_PORT=3306','-e','DB_NAME='+DB,
           '-e','DB_USERNAME=synthetic','-e','DB_PASSWORD='+dbpass,
           '-e','REDIS_HOST='+redis,'-e','REDIS_PORT=6379',
           '-e','REDIS_PASSWORD='+redispass,'-e','REDIS_DATABASE=0',
           '-e','YUDAO_PROVISIONING_ENABLED=true','-e','YUDAO_PROVISIONING_TOKEN='+machine_token,
           '-e','YUDAO_LICENSE_ENFORCE=false','-e','YUDAO_CAPTCHA_ENABLE=false',
           '-e','OPENAI_ENABLE_AI_ANALYSIS=false','-e','SERVER_SERVLET_SESSION_PERSISTENT=false',
           IMAGE)
    containers.append(server)
    for name in containers:
        inspect=json.loads(docker('inspect',name).stdout)
        item=inspect[0]
        check('container-'+name.rsplit('-',1)[-1]+'-has-no-mounts-or-host-ports',
              item['Mounts']==[] and not item['HostConfig']['PortBindings']
              and item['HostConfig']['NetworkMode']==net)
    check('d4a-source-jar-actually-running',
          docker('exec',server,'sha256sum','/app/app.jar').stdout.decode().split()[0]==EXPECTED_JAR)
    check('isolated-source-boot-healthy',wait_http())

    st,b=request('/admin-api/system/saas/internal/provisioning',method='POST',payload={})
    check('machine-token-absent-401',denied(st,b),detail(st,b))
    admin_a='d4aa'+tag
    admin_b='d4ab'+tag
    a=provision('A',admin_a,plan_id,990113)
    b=provision('B',admin_b,plan_id,990113)
    check('two-distinct-isolated-tenants',a!=b)
    expected_roles=('tenant_admin','tenant_rehab_therapist','tenant_rehab_clinical_reviewer')
    for label,tenant in (('A',a),('B',b)):
        role_rows=sql("SELECT CONCAT(code,':',data_scope,':',"
                      "(SELECT COUNT(*) FROM system_role_menu rm WHERE rm.role_id=r.id AND rm.deleted=b'0')) "
                      "FROM system_role r WHERE r.tenant_id="+str(tenant)+" AND r.deleted=b'0' ORDER BY code")
        lines=role_rows.splitlines()
        expected=['tenant_admin:1:35','tenant_rehab_clinical_reviewer:1:18',
                  'tenant_rehab_therapist:5:46']
        check('tenant-'+label+'-three-minimal-role-menus-and-scopes',lines==expected,
              'roles='+str(len(lines))+' menu_counts='+','.join(x.split(':')[-1] for x in lines))
        check('tenant-'+label+'-no-legacy-therapist-role',
              sql("SELECT COUNT(*) FROM system_role WHERE tenant_id="+str(tenant)+
                  " AND code='rehab_therapist' AND deleted=b'0'")=='0')

    access_a,admin_a_id=login(admin_a,adminpass,a,'A-admin')
    access_b,admin_b_id=login(admin_b,adminpass,b,'B-admin')
    for label,tenant,admin_id in (('A',a,admin_a_id),('B',b,admin_b_id)):
        check('tenant-'+label+'-bootstrap-admin-only',sql(
            "SELECT GROUP_CONCAT(r.code ORDER BY r.code) FROM system_user_role ur JOIN system_role r ON r.id=ur.role_id "
            "WHERE ur.user_id="+str(admin_id)+" AND ur.tenant_id="+str(tenant)+
            " AND ur.deleted=b'0'")=='tenant_admin')

    therapist_a_name='thera'+tag
    reviewer_a_name='reva'+tag
    therapist_a_id=create_user(access_a,a,therapist_a_name,'A-therapist')
    reviewer_a_id=create_user(access_a,a,reviewer_a_name,'A-reviewer')
    therapist_b_id=create_user(access_b,b,'therb'+tag,'B-therapist')

    role_path='/admin-api/system/user/assign-rehab-staff-role'
    st,body=request(role_path,method='POST',tenant=a,bearer=access_a,
                    payload={'userId':therapist_a_id,'roleCode':'tenant_rehab_therapist'})
    check('admin-can-assign-therapist-through-limited-HTTP-API',success(st,body),detail(st,body))
    st,body=request(role_path,method='POST',tenant=b,bearer=access_b,
                    payload={'userId':therapist_b_id,'roleCode':'tenant_rehab_therapist'})
    check('other-tenant-admin-can-assign-own-therapist',success(st,body),detail(st,body))
    st,body=request(role_path,method='POST',tenant=a,bearer=access_a,
                    payload={'userId':reviewer_a_id,'roleCode':'tenant_rehab_clinical_reviewer'})
    check('tenant-admin-cannot-self-grant-clinical-reviewer',not success(st,body),detail(st,body))
    st,body=request(role_path,method='POST',tenant=a,bearer=access_a,
                    payload={'userId':therapist_b_id,'roleCode':'tenant_rehab_therapist'})
    check('tenant-admin-cannot-assign-foreign-tenant-user',not success(st,body),detail(st,body))
    st,body=request(role_path,method='POST',tenant=a,bearer=access_a,
                    payload={'userId':admin_a_id,'roleCode':'tenant_rehab_therapist'})
    check('tenant-admin-cannot-change-own-role',not success(st,body),detail(st,body))
    check('unauthorized-reviewer-not-assigned',sql("SELECT COUNT(*) FROM system_user_role WHERE user_id="+
          str(reviewer_a_id)+" AND tenant_id="+str(a)+" AND deleted=b'0'")=='0')

    # Only a synthetic DBA fixture assigns reviewer after mimicking a separate
    # qualification approval. This does NOT test real credential verification.
    reviewer_role=sql("SELECT id FROM system_role WHERE tenant_id="+str(a)+
                      " AND code='tenant_rehab_clinical_reviewer' AND status=0 AND deleted=b'0'")
    check('exactly-one-reviewer-role-for-fixture',len(reviewer_role.splitlines())==1)
    sql("INSERT INTO system_user_role(user_id,role_id,tenant_id) VALUES ("+
        str(reviewer_a_id)+","+reviewer_role+","+str(a)+")")
    check('reviewer-role-assigned-only-in-synthetic-mysql',sql(
        "SELECT COUNT(*) FROM system_user_role WHERE user_id="+str(reviewer_a_id)+
        " AND role_id="+reviewer_role+" AND tenant_id="+str(a)+" AND deleted=b'0'")=='1')

    access_ta,_=login(therapist_a_name,staffpass,a,'A-therapist')
    access_ra,_=login(reviewer_a_name,staffpass,a,'A-reviewer')
    access_tb,_=login('therb'+tag,staffpass,b,'B-therapist')

    for label,tenant,access in (('A-admin',a,access_a),('A-therapist',a,access_ta),
                                 ('A-reviewer',a,access_ra),('B-admin',b,access_b),('B-therapist',b,access_tb)):
        st,body=request('/admin-api/system/auth/get-permission-info',tenant=tenant,bearer=access)
        check('permission-info-'+label,success(st,body) and isinstance(body.get('data'),dict),detail(st,body))

    st,body=request(role_path,method='POST',tenant=a,bearer=access_ta,
                    payload={'userId':reviewer_a_id,'roleCode':'tenant_rehab_therapist'})
    check('therapist-cannot-assign-staff-role',denied(st,body),detail(st,body))
    st,body=request(role_path,method='POST',tenant=b,bearer=access_a,
                    payload={'userId':therapist_b_id,'roleCode':'tenant_rehab_therapist'})
    check('forged-tenant-header-on-role-assignment-denied',denied(st,body),detail(st,body))

    stage_seed_patients(a,b,therapist_a_id)
    patient_page='/admin-api/rehab/patient/page?pageNo=1&pageSize=20'
    for label,tenant,access,expected in (
        ('A-admin',a,access_a,{991101,991102}),
        ('A-therapist',a,access_ta,{991101}),
        ('A-reviewer',a,access_ra,{991101,991102}),
        ('B-admin',b,access_b,{992101}),
        ('B-therapist-without-assignment',b,access_tb,set())):
        st,body,ids=page_ids(patient_page,tenant,access)
        check('patient-page-visible-'+label,success(st,body) and ids==expected,
              detail(st,body)+' visible='+str(sorted(ids)))

    for label,tid,access,foreign_id in (
        ('A-admin',a,access_a,992101),('A-therapist',a,access_ta,991102),
        ('A-reviewer',a,access_ra,992101),('B-admin',b,access_b,991101)):
        st,body=request('/admin-api/rehab/patient/get?id='+str(foreign_id),tenant=tid,bearer=access)
        check('foreign-or-unassigned-patient-detail-blocked-'+label,
              not success(st,body),detail(st,body))
    st,body=request('/admin-api/rehab/patient/page?pageNo=1&pageSize=20',
                    tenant=b,bearer=access_ra)
    check('reviewer-forged-tenant-header-rejected',denied(st,body),detail(st,body))

    report_page='/admin-api/rehab/report/page?pageNo=1&pageSize=20'
    for label,tenant,access,expected in (
        ('A-admin',a,access_a,{991401,991402}),
        ('A-therapist',a,access_ta,{991401}),
        ('A-reviewer',a,access_ra,{991401,991402}),
        ('B-admin',b,access_b,{992401})):
        st,body,ids=page_ids(report_page,tenant,access)
        check('report-page-visible-'+label,success(st,body) and ids==expected,
              detail(st,body)+' visible='+str(sorted(ids)))

    for label,tenant,access in (('A-admin',a,access_a),('A-therapist',a,access_ta)):
        st,body=request('/admin-api/rehab/report/review',method='POST',tenant=tenant,bearer=access,
                        payload={'id':991401,'remark':'SYNTHETIC_REVIEW'})
        check('report-review-forbidden-'+label,denied(st,body),detail(st,body))
        st,body=request('/admin-api/rehab/report/approve',method='POST',tenant=tenant,bearer=access,
                        payload={'id':991401,'remark':'SYNTHETIC_APPROVE'})
        check('report-approve-forbidden-'+label,denied(st,body),detail(st,body))

    st,body=request('/admin-api/rehab/report/review',method='POST',tenant=a,bearer=access_ra,
                    payload={'id':991401,'remark':'SYNTHETIC_REVIEW'})
    check('clinical-reviewer-report-review-succeeded',success(st,body),detail(st,body))
    check('report-review-record-and-actor-updated',sql("SELECT CONCAT(report_status,':',reviewed_by) FROM rehab_report WHERE id=991401")
          =='reviewed:'+str(reviewer_a_id))
    st,body=request('/admin-api/rehab/report/approve',method='POST',tenant=a,bearer=access_ra,
                    payload={'id':991401,'remark':'SYNTHETIC_APPROVED'})
    check('clinical-reviewer-report-approval-succeeded',success(st,body),detail(st,body))
    check('report-approval-record-and-actor-updated',sql("SELECT CONCAT(report_status,':',approved_by) FROM rehab_report WHERE id=991401")
          =='approved:'+str(reviewer_a_id))
    check('report-review-and-approval-audit-created',sql("SELECT COUNT(*) FROM rehab_audit_log WHERE "
          "module_type='report' AND module_id=991401 AND operator_user_id="+str(reviewer_a_id))=='2')
    st,body=request('/admin-api/rehab/report/review',method='POST',tenant=a,bearer=access_ra,
                    payload={'id':992401,'remark':'FORBIDDEN_FOREIGN_REPORT'})
    check('reviewer-cannot-review-other-tenant-report',business_denied(st,body),detail(st,body))
    check('other-tenant-report-remains-draft',sql("SELECT report_status FROM rehab_report WHERE id=992401")=='draft')

    for label,tenant,access in (('A-admin',a,access_a),('A-therapist',a,access_ta)):
        st,body=request('/admin-api/rehab/motion/sign',method='POST',tenant=tenant,bearer=access,
                        payload={'assessmentId':991501,'confirmed':True})
        check('motion-sign-forbidden-'+label,denied(st,body),detail(st,body))
    st,body=request('/admin-api/rehab/motion/sign',method='POST',tenant=a,bearer=access_ra,
                    payload={'assessmentId':992501,'confirmed':True})
    check('reviewer-cannot-sign-other-tenant-motion',business_denied(st,body),detail(st,body))
    st,body=request('/admin-api/rehab/motion/sign',method='POST',tenant=a,bearer=access_ra,
                    payload={'assessmentId':991501,'confirmed':True})
    check('clinical-reviewer-motion-sign-succeeded',success(st,body),detail(st,body))
    check('motion-sign-record-and-actor-updated',sql("SELECT CONCAT(status,':',signed_user_id) FROM rehab_motion_assessment WHERE id=991501")
          =='COMPLETED:'+str(reviewer_a_id))
    check('two-clinical-reports-produced-by-signer',sql("SELECT COUNT(*) FROM rehab_motion_report WHERE "
          "assessment_id=991501 AND signed_user_id="+str(reviewer_a_id)+" AND status='signed'")=='2')
    check('motion-sign-audit-created',int(sql("SELECT COUNT(*) FROM rehab_audit_log WHERE "
          "module_type='motion' AND module_id=991501 AND operation_type='sign' AND operator_user_id="+
          str(reviewer_a_id)))>=1)

    for name,state in before.items():
        check('live-instance-'+name+'-untouched',docker('inspect',name,'--format',
              '{{.Image}}|{{.State.Running}}').stdout.decode().strip()==state)
    check('test-instances-only-two-Owner-provisions',sql("SELECT COUNT(*) FROM saas_provisioning_requests WHERE status='ACTIVE'")=='2')


if __name__=='__main__':
    # Fail before creating any Docker resources or an evidence file unless the
    # explicit destructive-to-disposable-data-only scope flag is provided.
    if len(sys.argv)!=2 or sys.argv[1]!='--synthetic-only':
        print('D4A_SYNTHETIC_ONLY_FLAG_REQUIRED',file=sys.stderr)
        sys.exit(2)
    EVIDENCE.mkdir(parents=True,exist_ok=True)
    EVIDENCE.chmod(0o700)
    passed=False
    try:
        run()
        passed=True
    except Exception as exc:
        print('TEST_STOP_CATEGORY='+str(exc).split(':',1)[0][:115],file=sys.stderr,flush=True)
    finally:
        for name in reversed(containers):
            try:docker('rm','-f',name,timeout=25)
            except Exception:print('CLEANUP_WARNING_CONTAINER',file=sys.stderr)
        if network_created:
            try:docker('network','rm',net,timeout=25)
            except Exception:print('CLEANUP_WARNING_NETWORK',file=sys.stderr)
        leftovers=[name for name in (mysql,redis,server)
                   if docker('inspect',name,check=False,timeout=7).returncode==0]
        network_left=docker('network','inspect',net,check=False,timeout=7).returncode==0
        checks.append({'name':'temporary-resources-cleaned','passed':not leftovers and not network_left,
                       'note':'remaining_containers='+str(len(leftovers))+' remaining_network='+str(int(network_left))})
        summary={'schema':'rehab-d4a-synthetic-role-http-e2e/1',
                 'source_sha':SOURCE_SHA,'candidate_image':IMAGE,
                 'candidate_image_id':EXPECTED_IMAGE,'candidate_jar_sha256':EXPECTED_JAR,
                 'isolated':True,'synthetic_only':True,'network_internal':True,
                 'no_host_ports':True,'no_business_mounts':True,
                 'passed':passed and not leftovers and not network_left,
                 'checks':checks,
                 'timestamp_utc':time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime())}
        p=EVIDENCE/('d4a-role-http-result-'+tag+'.json')
        fd=os.open(p,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
        with os.fdopen(fd,'w',encoding='utf-8') as f:json.dump(summary,f,indent=2,ensure_ascii=False)
        print('SUMMARY_PASSED='+str(summary['passed']))
        print('PASSED_CHECKS='+str(sum(bool(x['passed']) for x in checks))+'/'+str(len(checks)))
        print('EVIDENCE_FILENAME='+p.name)
        if not summary['passed']:sys.exit(2)
