"""Minimal anvil reader for 26.3 chunks (sections with block_states palette/data)."""
import zlib, struct, io, os, nbtlib

def chunks(path):
    with open(path, 'rb') as f:
        data = f.read()
    for i in range(1024):
        off = struct.unpack('>I', b'\0' + data[i*4:i*4+3])[0]
        if off == 0:
            continue
        o = off * 4096
        length = struct.unpack('>I', data[o:o+4])[0]
        comp = data[o+4]
        raw = data[o+5:o+4+length]
        if comp == 2:
            raw = zlib.decompress(raw)
        elif comp == 1:
            import gzip; raw = gzip.decompress(raw)
        elif comp == 3:
            pass
        else:
            continue
        yield nbtlib.File.parse(io.BytesIO(raw))

def section_blocks(sec):
    bs = sec.get('block_states')
    if bs is None:
        return None, None
    pal = bs['palette']
    if 'data' not in bs or len(pal) == 1:
        return pal, [0]*4096
    bits = max(4, (len(pal)-1).bit_length())
    per = 64 // bits
    mask = (1 << bits) - 1
    out = []
    for long in bs['data']:
        v = int(long) & 0xFFFFFFFFFFFFFFFF
        for j in range(per):
            out.append((v >> (j*bits)) & mask)
            if len(out) == 4096:
                break
        if len(out) == 4096:
            break
    return pal, out

def norm(p):
    """(id, {props}) from a 26.3 palette entry: a string, {'': string} or {id, properties}."""
    if isinstance(p, str):
        return str(p), {}
    if '' in p:
        return str(p['']), {}
    if 'id' in p:
        return str(p['id']), {str(k): str(v) for k, v in p.get('properties', {}).items()}
    return str(p['Name']), {str(k): str(v) for k, v in p.get('Properties', {}).items()}

def pname(p):
    return norm(p)[0]

def state_str(p):
    n, props = norm(p)
    return n + ('[' + ','.join(f'{k}={v}' for k, v in sorted(props.items())) + ']' if props else '')
