"""Independent test-only integer reference of RFC 9380 section 6.6.2, BIGN profile.
No reference curve arithmetic is used by the application.
"""
import ctypes
import json
from pathlib import Path
import random
P=2**256-189
A=P-3
B=int.from_bytes(bytes.fromhex('F1039CD66B7D2EB253928B976950F54CBEFBD8E4AB3AC1D2EDA8F315156CCE77'),'little')
Z=-11

def trim(t):
    while t and not t[-1]: t.pop()
    return t

def mod(t,f):
    t=trim(t[:])
    while len(t)>=len(f):
        v=t[-1]*pow(f[-1],-1,P)%P;off=len(t)-len(f)
        for i in range(len(f)):t[off+i]=(t[off+i]-v*f[i])%P
        trim(t)
    return t

def mul(x,y,f):
    t=[0]*(len(x)+len(y)-1)
    for i,u in enumerate(x):
        for j,v in enumerate(y):t[i+j]=(t[i+j]+u*v)%P
    return mod(t,f)

def power(x,n,f):
    r=[1]
    while n:
        if n&1:r=mul(r,x,f)
        x=mul(x,x,f);n>>=1
    return r

def check_z():
    assert pow(Z%P,(P-1)//2,P)==P-1 and Z%P!=P-1
    f=[(B-Z)%P,A,0,1];g=power([0,1],P,f)
    g += [0]*(2-len(g));g[1]=(g[1]-1)%P;trim(g)
    while g:f,g=g,mod(f,g)
    assert len(f)==1 # cubic has no roots => irreducible
    x=B*pow(Z*A%P,-1,P)%P
    assert pow((x*x*x+A*x+B)%P,(P-1)//2,P) in (0,1)

def map_u(u):
    t=(Z*Z*pow(u,4,P)+Z*u*u)%P
    inv=pow(t,P-2,P)
    x=(-B*pow(A,-1,P)*(1+inv))%P if t else B*pow(Z*A%P,-1,P)%P
    g=(x**3+A*x+B)%P
    if pow(g,(P-1)//2,P) not in (0,1):
        x=Z*u*u*x%P;g=(x**3+A*x+B)%P
    y=pow(g,(P+1)//4,P)
    assert y*y%P==g
    if (y&1)!=(u&1):y=P-y
    return x,y

def add(a,b):
    x,y=a;v,w=b
    if x==v and (y+w)%P==0:return None
    m=((w-y)*pow((v-x)%P,-1,P) if x!=v else (3*x*x+A)*pow(2*y,-1,P))%P
    q=(m*m-x-v)%P
    return q,(m*(x-q)-y)%P

def reference(data):
    a=map_u(int.from_bytes(data[:48],'big')%P)
    b=map_u(int.from_bytes(data[48:],'big')%P)
    q=add(a,b)
    return b''.join(v.to_bytes(32,'little') for v in q) if q else None

if __name__=='__main__':
    check_z()
    root=Path(__file__).resolve().parents[2]
    lib=ctypes.CDLL(str(root/'.tools/bee2-mapping-host/libsc_hash_reference.dylib'))
    call=lib.sc_hash_group;call.argtypes=[ctypes.c_void_p,ctypes.c_void_p,ctypes.c_size_t];call.restype=ctypes.c_int
    rng=random.Random(9380)
    data=[bytes(96),bytes(range(96)),bytes([255])*96]
    # Exceptional u1=0 and independently varied u0, plus nontrivial pairs.
    data += [i.to_bytes(48,'big')+bytes(48) for i in range(1,20)]
    data += [rng.randbytes(96) for _ in range(100)]
    vectors=[]
    for value in data:
        expected=reference(value);out=ctypes.create_string_buffer(64)
        code=call(out,value,96)
        assert code==0 and out.raw==expected, (value.hex(),code,out.raw.hex(),expected.hex())
        vectors.append({'uniform':value.hex(),'point':expected.hex()})
    assert call(out,bytes(95),95)!=0
    (root/'native/bee2-mapping/hash-group-vectors.json').write_text(json.dumps({'profile':'BIGN128_XMD:BELT_SSWU_RO_v1','Z':Z,'vectors':vectors[:3]},indent=2)+'\n')
    print(f'PASS: Z criteria and {len(data)} independently computed SSWU sum vectors')
