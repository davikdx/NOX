"""Compute the APK v2 content digest, or insert a public detached signature block.
The Android SDK apksigner must independently verify the result before distribution.
"""
from pathlib import Path
from hashlib import sha256
from struct import pack, unpack_from
import argparse
import base64
import json

def sections(data):
    end = data.rfind(b"PK\x05\x06", max(0,len(data)-65557))
    if end < 0 or end+22+unpack_from("<H",data,end+20)[0]!=len(data):
        raise ValueError("ZIP sem registro final válido")
    cd = unpack_from("<I",data,end+16)[0]
    if data[cd:cd+4] != b"PK\x01\x02" or data[cd-16:cd] == b"APK Sig Block 42":
        raise ValueError("Esperado APK ZIP alinhado e ainda sem assinatura")
    return data[:cd], data[cd:end], data[end:]

def digest(regions):
    chunks=[]
    for region in regions:
        for offset in range(0,len(region),1<<20):
            chunk=region[offset:offset+(1<<20)]
            chunks.append(sha256(b"\xa5"+pack("<I",len(chunk))+chunk).digest())
    return sha256(b"\x5a"+pack("<I",len(chunks))+b"".join(chunks)).hexdigest()

if __name__ == "__main__":
    ap=argparse.ArgumentParser()
    ap.add_argument("apk",type=Path)
    ap.add_argument("--public-envelope",type=Path)
    ap.add_argument("--out",type=Path)
    args=ap.parse_args()
    raw=args.apk.read_bytes()
    head,directory,end=sections(raw)
    fingerprint=digest((head,directory,end))
    print("NOX_UNSIGNED_SHA256="+sha256(raw).hexdigest())
    print("NOX_V2_CONTENT_DIGEST="+fingerprint)
    if args.public_envelope:
        env=json.loads(args.public_envelope.read_text())
        if sha256(raw).hexdigest()!=env["apkSha256"] or fingerprint!=env["v2Digest"]:
            raise ValueError("O conteúdo compilado difere dos bytes assinados pelo titular")
        block=base64.b64decode(env["block"],validate=True)
        args.out.write_bytes(head+block+directory+end[:16]+pack("<I",len(head)+len(block))+end[20:])

