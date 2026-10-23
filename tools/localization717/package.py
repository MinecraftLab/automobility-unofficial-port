"""Reassemble one exact717 localization artifact from explicit original inputs.
No downloading, compilation, process launch, decoder, game or runtime admission proof.
The three archive/class-pool routines preserve the qualified717 assembly algorithm.
Never use Python -O: this deliberately retains its original strict assertions.
"""
import copy,hashlib,io,json,struct,sys,zipfile
from pathlib import Path
if not __debug__: raise RuntimeError("Assertions are required for exact717 packaging")
def require(ok,message):
    if not ok: raise ValueError(message)
def sha(b):return hashlib.sha256(b).hexdigest().upper()
def constant_patch(data,replacements):
 """Keep pool indices and the complete fields/methods/attributes byte tail exact."""
 assert data[:4]==b'\xca\xfe\xba\xbe'
 count=struct.unpack('>H',data[8:10])[0];offset=10;index=1;out=bytearray(data[:10]);changes=[];seen=set()
 while index<count:
  start=offset;tag=data[offset];offset+=1
  if tag==1:
   size=struct.unpack('>H',data[offset:offset+2])[0];offset+=2;raw=data[offset:offset+size];offset+=size
   match=next((r for r in replacements if r['old'].encode('utf-8')==raw),None)
   if match:
    new=match['resolved'].encode('utf-8');assert len(new)<=65535
    out.extend(b'\x01'+struct.pack('>H',len(new))+new);seen.add(match['old'])
    if new!=raw:changes.append(dict(poolIndex=index,before=match['old'],after=match['resolved']))
   else:out.extend(data[start:offset])
  else:
   size={3:4,4:4,5:8,6:8,7:2,8:2,9:4,10:4,11:4,12:4,15:3,16:2,17:4,18:4,19:2,20:2}[tag]
   offset+=size;out.extend(data[start:offset])
  index+=2 if tag in (5,6) else 1
 tail=data[offset:];out.extend(tail)
 assert seen=={r['old'] for r in replacements}
 return bytes(out),dict(constantUtf8Changes=changes,fieldsMethodsAttributesTailSha256=sha(tail),tailUnchanged=True)

def changed_members(before,after,allow):
 bn=set(before.namelist());an=set(after.namelist());assert len(bn)==len(before.namelist()) and len(an)==len(after.namelist())
 changes=[]
 for name in sorted(bn|an):
  a=before.read(name) if name in bn else None;b=after.read(name) if name in an else None
  if a!=b:
   assert name in allow,('Unexpected archive delta',name)
   changes.append(dict(member=name,beforeSha256=sha(a) if a is not None else None,afterSha256=sha(b) if b is not None else None))
 return changes

def rewrite(source,overlays,remove=()):
 """Preserve all payloads and metadata of unaffected entries, including audio."""
 result=io.BytesIO()
 with zipfile.ZipFile(io.BytesIO(source)) as before,zipfile.ZipFile(result,'w') as after:
  assert len(before.namelist())==len(set(before.namelist()));after.comment=before.comment
  assert not any(n.upper().startswith('META-INF/') and n.upper().endswith(('.SF','.RSA','.DSA','.EC')) for n in before.namelist())
  for info in before.infolist():
   if info.filename not in remove:after.writestr(copy.copy(info),overlays.get(info.filename,before.read(info)))
  for name in sorted(set(overlays)-set(before.namelist())):
   info=zipfile.ZipInfo(name,date_time=(2026,10,22,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED
   after.writestr(info,overlays[name])
 return result.getvalue()

def main():
    import argparse
    ap=argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--manifest',required=True)
    ap.add_argument('--baseline',required=True)
    ap.add_argument('--output',required=True)
    ap.add_argument('--ui-class')
    a=ap.parse_args(); manifest_path=Path(a.manifest).resolve()
    root=manifest_path.parents[2]
    m=json.loads(manifest_path.read_text(encoding='utf-8'))
    require(m['schema']=='offchunk-localization717-package/1','manifest schema')
    def pinned(path,row):
        path=Path(path); require(path.is_file() and not path.is_symlink(),'regular input required')
        b=path.read_bytes(); require(len(b)==row['bytes'] and sha(b)==row['sha256'],'input pin mismatch')
        return b
    def owned(row):
        path=(root/row['path']).resolve(); require(path.is_relative_to(root),'resource escaped source root')
        return pinned(path,row)
    source=pinned(a.baseline,m['baseline'])
    overlays={r['member']:owned(r['source']) for r in m['resources']['parent']}
    overlays['fabric.mod.json']=owned(m['metadata'])
    remove=[]
    with zipfile.ZipFile(io.BytesIO(source)) as before:
        if m['nested']:
            n=m['nested']; child=before.read(n['oldMember'])
            require(len(child)==n['bytes'] and sha(child)==n['sha256'],'nested baseline mismatch')
            child_overlays={r['member']:owned(r['source']) for r in m['resources']['nested']}
            child_overlays['fabric.mod.json']=owned(m['nestedMetadata'])
            child_output=rewrite(child,child_overlays)
            with zipfile.ZipFile(io.BytesIO(child)) as b,zipfile.ZipFile(io.BytesIO(child_output)) as c:
                changed_members(b,c,set(m['allowedNestedChangedMembers']))
            require(sha(child_output)==m['expectedNestedSha256'],'nested output mismatch')
            overlays[n['newMember']]=child_output
            if n['oldMember']!=n['newMember']: remove.append(n['oldMember'])
        for row in m['classPatches']:
            original=before.read(row['member'])
            require(sha(original)==row['baselineSha256'],'class baseline mismatch')
            overlays[row['member']],_=constant_patch(original,row['replacements'])
        if m.get('uiClass'):
            require(a.ui_class is not None,'exact separately compiled UI outer class required')
            overlays[m['uiClass']['member']]=pinned(a.ui_class,m['uiClass'])
    output=rewrite(source,overlays,remove)
    with zipfile.ZipFile(io.BytesIO(source)) as b,zipfile.ZipFile(io.BytesIO(output)) as c:
        changes=changed_members(b,c,set(m['allowedChangedMembers']))
        require(json.loads(c.read('fabric.mod.json'))['version']==m['version'],'metadata version mismatch')
        for name,expected in overlays.items(): require(c.read(name)==expected,'overlay mismatch')
    require(len(output)==m['artifact']['bytes'] and sha(output)==m['artifact']['sha256'],
            'output differs from the published717 artifact; do not relabel or weaken admission')
    target=Path(a.output); require(not target.exists(),'output must be fresh')
    with target.open('xb') as f: f.write(output)
    print(json.dumps(dict(status='EXACT_ASSEMBLY_BYTES_ONLY_NOT_JVM_PROOF',sha256=sha(output),changed=changes)))
if __name__=='__main__': main()
