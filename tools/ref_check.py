"""Onafhankelijke referentie-checker (naieve, 3x uitgerolde implementatie) voor differentiële test."""
import json, math, random, subprocess, sys, re
T=lambda s:int(s[:2])*60+int(s[3:])
def load(path):
    cells=[];grid=[]
    for line in open(path):
        line=line.strip()
        if not line or line.startswith('#'): continue
        f=line.split(';')[1:]
        for col,t in enumerate(f):
            m=re.match(r'^(\d+)\s+(\d\d:\d\d)-(\d\d:\d\d)$',t)
            if m:
                s,e=T(m.group(2)),T(m.group(3))
                if e<=s:e+=1440
                cells.append(dict(col=col,code=m.group(1),kind='S',s=s,e=e,d=e-s))
            else:
                cells.append(dict(col=col,code=t,kind=t,s=0,e=0,d=0))
            grid.append(len(cells)-1)
    return cells,grid
REST_CLASS={'RH':0,'RR':0,'RH/CH':0,'RR/CV':0,'CH':1,'CV':1,'CY':2}
def evaluate(cells,grid):
    N=len(grid);W=N//7
    H=[0]*25;Z=[0.0]*13
    D=[cells[grid[d%N]] for d in range(3*N)]    # 3 maal uitgerold
    mid=range(N,2*N)
    isS=lambda d:D[d]['kind']=='S'
    rc=lambda d:REST_CLASS[D[d]['kind']]
    # H2, Z4
    for w in range(W):
        h=sum(D[N+w*7+i]['d'] for i in range(7))
        if h>3000:H[1]+=1
        if h/60>42:Z[3]+=1.5*(h/60-42)**2;H[12+3]+=1
    for s in mid:
        h=sum(D[s+i]['d'] for i in range(7))/60
        if h>42:Z[3]+=0.1*(h-42)**2
    # paren
    for p in mid:
        if not isS(p):continue
        n=p+1
        while not isS(n):n+=1
        k=n-p-1
        gap=(n-p)*1440+D[n]['s']-D[p]['e']
        if gap<840:H[0]+=1
        dh=(D[n]['s']-D[p]['s'])/60
        z1=0
        if k==1:z1+=12+3*max(0,abs(dh)-2)
        if dh>0:z1+=max(0,dh-2.5*(n-p))**1.5
        if dh<0:z1+=1.5*max(0,-dh-1.2*(n-p))**1.7
        Z[0]+=z1
        cl=lambda x:0 if x<480 else 2 if x>=780 else 1
        cp_,cn_=cl(D[p]['s']),cl(D[n]['s'])
        direct=(cp_==0 and cn_==2) or (cp_==2 and cn_==0)
        if cp_!=cn_:Z[12]+=3 if direct else 1
        if direct:H[12+12]+=1
        if k==1 and direct:H[12+0]+=1
        if gap<900:
            H[12+7]+=1
            Z[7]+=(2 if gap<870 else 1)+(2 if D[n]['s']<300 else 0)
        if k>0:
            cls=[rc(x) for x in range(p+1,n)]
            base=max([2160,2040,2280][c] for c in cls)
            if gap<base+(k-1)*1440:H[2]+=1
            if k>3:H[6]+=1
            if not (k>=3 and 0 not in cls):
                if D[p]['e']>[1200,1380,1500][cls[0]]:H[3]+=1
                if D[n]['s']<[360,300,180][cls[-1]]:H[4]+=1
            if cls[-1]==2 and D[n]['d']>480:H[5]+=1
            if k==3:Z[2]+=3;H[12+2]+=1
    # H9
    for d in mid:
        if not isS(d) and rc(d)==0:
            e=d+1
            while not(not isS(e) and rc(e)==0):e+=1
            if e-d-1>8:H[7]+=1
    # runs
    for s in mid:
        if isS(s) and not isS(s-1):
            L=0
            while isS(s+L):L+=1
            if L>7:H[8]+=L-7
            night=[D[s+i]['s']<240 or D[s+i]['e']>1380 for i in range(L)]
            if L>5 and any(all(night[i:i+5]) for i in range(L-4)):H[8]+=1
            if L==4:Z[8]+=3
            if L>=5:Z[8]+=8+25*(L-5);H[12+8]+=1
            r=0
            for i in range(L+1):
                if i<L and D[s+i]['s']<300:r+=1
                else:
                    if r>1:Z[5]+=3*(r-1)**2
                    r=0
            r=0
            for i in range(L+1):
                if i<L and D[s+i]['e']>1440:r+=1
                else:
                    if r>1:Z[6]+=3*(r-1)**2
                    r=0
    for d in mid:
        if isS(d):
            s=D[d]['s'];e=D[d]['e']
            Z[5]+=3 if s<240 else 2 if s<270 else 1 if s<300 else 0
            Z[6]+=(2 if e>1440 else 0)+(1 if e>1500 else 0)+(1 if e>1530 else 0)
            if isS(d+1) and D[d]['d']>=510 and D[d+1]['d']>=510:Z[10]+=1;H[12+10]+=1
            if isS(d+1) and D[d]['s']<300 and D[d+1]['s']<300:H[12+5]+=1
            if isS(d+1) and D[d]['e']>1440 and D[d+1]['e']>1440:H[12+6]+=1
        if not isS(d) and not isS(d+1) and rc(d)==0 and rc(d+1)==0:Z[2]+=2;H[12+2]+=1
        c7=sum(isS(d+i) for i in range(7));c8=sum(isS(d+i) for i in range(8))
        if c7>=6:H[12+1]+=1
        if c7==6:Z[1]+=6
        if c7==7:Z[1]+=20
        if c8>=7:Z[1]+=14
    # weekends
    free=[]
    for w in range(W):
        sat=N+w*7+5;sun=sat+1
        if isS(sat)!=isS(sun):
            Z[4]+=14;H[12+4]+=1
        ok=False
        if not isS(sat) and not isS(sun) and D[sat]['kind']!='CY' and D[sun]['kind']!='CY':
            pd=sat-1
            while not isS(pd):pd-=1
            nd=sun+1
            while not isS(nd):nd+=1
            endp=pd*1440+D[pd]['e'];startn=nd*1440+D[nd]['s']
            ok=endp<=(sat-1)*1440+1380 and startn>=(sun+1)*1440+300 and startn-endp>=3480
        free.append(ok)
    Z[9]=-5*sum(free)
    worked=[isS(N+w*7+5) or isS(N+w*7+6) for w in range(W)]
    if all(worked):Z[11]+=8*(W-1)**2;H[12+11]+=W
    else:
        for w in range(W,2*W):
            if worked[w%W] and not worked[(w-1)%W]:
                L=0
                while worked[(w+L)%W] and L<W:L+=1
                if L>=2:Z[11]+=8*(L-1)**2;H[12+11]+=L-1
    for w in range(W):
        if not any(free[(w+k)%W] for k in range(4)):H[9]+=1
    idx=[w for w in range(W) if free[w]]
    for a,b in zip(idx,idx[1:]+[idx[0]+W] if idx else []):
        if b-a>5:H[9]+=1
    # H12
    cy=[w for w in range(W) if any(cells[grid[w*7+c]]['kind']=='CY' for c in range(7))]
    # (CY per dag; meerdere CY in dezelfde week tellen via dagen)
    cyd=[d for d in range(N) if cells[grid[d]]['kind']=='CY']
    for i,d in enumerate(cyd):
        nxt=cyd[(i+1)%len(cyd)]+(N if i+1>=len(cyd) else 0)
        if len(cyd)==1:
            nxt=d+N
        if nxt//7-d//7<4:H[10]+=1
    return H,Z,sum(free)
def counts(cells,grid):
    N=len(grid);W=N//7
    def mn(L,kinds):
        return min(sum(cells[grid[((w+k)%W)*7+c]]['kind'] in kinds for k in range(L) for c in range(7)) for w in range(W))
    return mn(4,('RH','RH/CH')),mn(8,('RH','RH/CH')),mn(4,('CH','RH/CH')),mn(13,('CH','RH/CH'))
def h13(cells,grid,mins,H):
    N=len(grid);W=N//7
    for L,kinds,m in ((4,('RH','RH/CH'),mins[0]),(8,('RH','RH/CH'),mins[1]),(4,('CH','RH/CH'),mins[2]),(13,('CH','RH/CH'),mins[3])):
        for w in range(W):
            if sum(cells[grid[((w+k)%W)*7+c]]['kind'] in kinds for k in range(L) for c in range(7))<m:H[11]+=1
if __name__=='__main__':
    path='reeks/src/main/resources/reeks_A.txt'
    cells,orig=load(path)
    mins=counts(cells,orig)
    import os; classpath=os.environ['CLASSPATH']
    random.seed(7)
    bad=0
    for t in range(60):
        g=orig[:]
        mode=t%3
        if t==0: pass
        elif mode==0:      # kolomgewijs random swaps
            for _ in range(random.randint(1,6)):
                c=random.randrange(7);a,b=random.sample(range(len(g)//7),2)
                g[a*7+c],g[b*7+c]=g[b*7+c],g[a*7+c]
        elif mode==1:      # volledige random permutatie per kolom
            for c in range(7):
                col=[g[w*7+c] for w in range(len(g)//7)];random.shuffle(col)
                for w,x in enumerate(col):g[w*7+c]=x
        else:              # weekswaps
            for _ in range(random.randint(1,4)):
                a,b=random.sample(range(len(g)//7),2)
                for c in range(7):g[a*7+c],g[b*7+c]=g[b*7+c],g[a*7+c]
        H,Z,free=evaluate(cells,g);h13(cells,g,mins,H)
        open('/tmp/perm.txt','w').write(' '.join(map(str,g)))
        out=subprocess.run(['java','-cp',classpath,'be.reeks.EvalTool',path,'/tmp/perm.txt'],capture_output=True,text=True).stdout.split('\n')
        jh=list(map(int,out[0].split()[1:]));jz=list(map(float,out[1].split()[1:]));jf=int(out[2].split()[1])
        # Java H-volgorde: H1,H2,H3,H4,H5,H7,H8,H9,H10,H11,H12,H13 == zelfde als python index
        okH=jh==H
        okZ=all(abs(a-b)<1e-3 for a,b in zip(jz,Z)) and jf==free
        if not(okH and okZ):
            bad+=1
            print('VERSCHIL t=',t,'\n java H',jh,'\n  py  H',H)
            print(' java Z',[round(x,2) for x in jz],jf,'\n  py  Z',[round(x,2) for x in Z],free)
    print('klaar, verschillen:',bad)
