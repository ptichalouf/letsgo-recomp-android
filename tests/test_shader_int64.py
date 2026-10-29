"""Host regression, synthetic Maxwell instructions only (no game assets).

Run: python3 tests/test_shader_int64.py --base adbf12dd
Requires a host C/C++ compiler. The base must already exist in local git.
"""
import argparse
import os
from pathlib import Path
import shutil
import struct
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
MX = 'src/runtime/nvn/maxwell/'
BASE = 'adbf12dd'


def run(*args, **kw):
    return subprocess.check_output(args, text=True, **kw)


# The fallback switch is optional here so this same harness compiles on the base:
# a base run fails on generated 64-bit arithmetic, not on a missing new API.
DRIVER = r'''
#include "translate.c"
int main(int argc, char **argv) {
    uint64_t w = strtoull(argv[1], NULL, 16);
    int stage = atoi(argv[2]);
    maxwell_gmem_map_all = atoi(argv[3]);
#ifdef HAS_NO_INT64
    maxwell_glsl_no_int64 = atoi(argv[4]);
#endif
    maxwell_gmem_base = strtoull(argv[6], NULL, 16);
    maxwell_gmem_size = strtoull(argv[7], NULL, 16);
    ShaderKey key = {0}; key.glsl = atoi(argv[5]);
    if (argc > 8) {
        ShaderInfo info = {0}; Ctx c = {0};
        c.info = &info; c.key = &key; c.stage = stage; c.glsl = key.glsl;
        emit_insn(&c, w, 8);
        if (c.fail) { fprintf(stderr, "%s\n", info.error); return 1; }
        puts(c.body.buf); free(c.body.buf); return 0;
    }
    uint64_t blob[24] = {0};
    unsigned first = stage == STAGE_COMPUTE ? 0 : 16;
    blob[first + 1] = w;
    blob[first + 2] = 0xe30000000007000full; /* EXIT, predicate PT */
    ShaderInfo *info = maxwell_translate((uint8_t *)blob,
                                       (first + 3) * 8, stage, &key);
    if (!info->msl) { fprintf(stderr, "%s\n", info->error); return 1; }
    puts(info->msl); maxwell_free(info); return 0;
}
'''


def instructions():
    pt = 7 << 16
    # IADD32I: CC/X/negation and unsigned immediate boundaries.
    for imm in (0, 1, 0x7fffffff, 0x80000000, 0xffffffff):
        for flags in range(8):
            yield 'add32', (0x1c00000000000000 | pt | 2 << 8 | 1
                            | imm << 20 | (flags & 1) << 52
                            | ((flags >> 1) & 1) << 53
                            | ((flags >> 2) & 1) << 56)
    for flags in range(32):
        yield 'add3', (0x5cc0000000000000 | pt | 2 << 8 | 3 << 20
                       | 4 << 39 | 1 | (flags & 1) << 47
                       | ((flags >> 1) & 1) << 48
                       | ((flags >> 2) & 7) << 49)
    for right in (False, True):
        for width64 in (False, True):
            for signed in (False, True):
                for wrap in (False, True):
                    yield 'shift', ((0x5cf8000000000000 if right else 0x5bf8000000000000)
                                    | pt | 2 << 8 | 3 << 20 | 4 << 39 | 1
                                    | int(width64) << 38 | int(signed) << 37
                                    | int(wrap) << 50)
    for store in (False, True):
        for wide in (False, True):
            for rz in (False, True):
                for size in range(8):
                    for off in (-0x800000, -1, 0, 1, 0x7fffff):
                        yield 'memory', (0xeed0000000000000 | int(store) << 51
                                         | pt | (255 if rz else 2) << 8 | 1
                                         | int(wide) << 45 | size << 48
                                         | (off & 0xffffff) << 20)


class Int64Tests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory(prefix='shader-int64-')
        cls.work = Path(cls.tmp.name)
        cls.bins = []
        for name, base in (('base', True), ('current', False)):
            folder = cls.work / name
            folder.mkdir()
            for file in ('translate.c', 'shader.h'):
                data = (run('git', 'show', f'{BASE}:{MX}{file}', cwd=ROOT)
                        if base else (ROOT / MX / file).read_text())
                (folder / file).write_text(data)
            (folder / 'driver.c').write_text(DRIVER)
            flags = (['-DHAS_NO_INT64'] if 'bool maxwell_glsl_no_int64;'
                     in (folder / 'translate.c').read_text() else [])
            binary = folder / 'driver'
            run(os.environ.get('CC', 'cc'), '-std=gnu11', '-O2', *flags,
                str(folder / 'driver.c'), '-lm', '-o', str(binary))
            cls.bins.append(binary)

    @classmethod
    def tearDownClass(cls):
        cls.tmp.cleanup()

    def emit(self, binary, word, stage=5, mapped=0, no64=0, glsl=1,
             base=0x7240000000, size=0xd0000000, snippet=False):
        return run(str(binary), f'{word:x}', str(stage), str(mapped),
                   str(no64), str(glsl), f'{base:x}', f'{size:x}',
                   *(['snippet'] if snippet else []))

    def test_present_byte_identical(self):
        count = 0
        for _, word in instructions():
            for stage in (0, 1, 5):
                for mapped in (0, 1):
                    with self.subTest(word=f'{word:x}', stage=stage, mapped=mapped):
                        self.assertEqual(self.emit(self.bins[0], word, stage, mapped),
                                         self.emit(self.bins[1], word, stage, mapped))
                        count += 1
        print(f'Int64 present: {count} complete GLSL outputs byte-identical', flush=True)

    def test_absent_no_64bit_math(self):
        count = 0
        for _, word in instructions():
            for mapped in (0, 1):
                text = self.emit(self.bins[1], word, mapped=mapped, no64=1)
                # An enabled extension is harmless; actual types/literals require Int64.
                self.assertNotRegex(text, r'\b(?:u?int64_t|ulong|long)\b|\b0x[0-9a-f]+ul\b')
                count += 1
        print(f'Int64 absent: {count} complete GLSL outputs without 64-bit math', flush=True)

    def test_absent_spirv_has_no_int64(self):
        compiler = os.environ.get('GLSLANG_VALIDATOR', shutil.which('glslangValidator'))
        validator = os.environ.get('SPIRV_VAL', shutil.which('spirv-val'))
        if not compiler or not validator:
            self.skipTest('set GLSLANG_VALIDATOR and SPIRV_VAL for SPIR-V validation')
        count = 0
        for _, word in instructions():
            for mapped in (0, 1):
                text = self.emit(self.bins[1], word, mapped=mapped, no64=1)
                shader = self.work / 'synthetic.comp'
                binary = self.work / 'synthetic.spv'
                shader.write_text(text)
                run(compiler, '--quiet', '-V', '--target-env', 'vulkan1.1', str(shader), '-o', str(binary))
                run(validator, '--target-env', 'vulkan1.1', str(binary))
                words = struct.unpack('<' + 'I' * (binary.stat().st_size // 4), binary.read_bytes())
                pos = 5
                caps = set()
                while pos < len(words):
                    length, opcode = words[pos] >> 16, words[pos] & 0xffff
                    self.assertGreater(length, 0)
                    if opcode == 17:  # OpCapability
                        caps.add(words[pos + 1])
                    pos += length
                self.assertNotIn(11, caps)  # Int64
                count += 1
        print(f'Int64 absent: {count} SPIR-V modules validated, none declares Int64', flush=True)

    def test_device_feature_wired_to_both_translation_paths(self):
        device = (ROOT / 'src/runtime/nvn/vulkan/device.c').read_text()
        exec_c = (ROOT / 'src/runtime/nvn/vulkan/exec.c').read_text()
        self.assertIn('b->shaderInt64 = h->shaderInt64;', device)
        self.assertIn('g_vk.shader_int64 = h->shaderInt64;', device)
        self.assertEqual(exec_c.count('maxwell_glsl_no_int64 = !g_vk.shader_int64;'), 2)

    def test_msl_unchanged(self):
        for _, word in instructions():
            self.assertEqual(self.emit(self.bins[0], word, glsl=0),
                             self.emit(self.bins[1], word, glsl=0, no64=1))

    def test_generated_numeric_equivalence(self):
        functions = []
        calls = []
        count = 0
        # Compile the ACTUAL emitted arithmetic, not a separately copied model.
        for kind, word in instructions():
            configs = ((0, 0), (0x7240000000, 0xd0000000),
                       (0xffffffff, 0x180000000)) if kind == 'memory' else ((0, 0),)
            for base, size in configs:
                outputs = []
                for no64 in (0, 1):
                    body = self.emit(self.bins[1], word, no64=no64,
                                     base=base, size=size, snippet=True)
                    if kind == 'memory':
                        if no64:
                            body = '\n'.join(body.splitlines()[:3])
                            body += '\nreturn Result{gl, gh, uint(ok), gw}; }'
                        else:
                            body = body.splitlines()[0]
                            body += '\nreturn Result{uint(ga), uint(ga >> 32), uint(ok), uint(ga >> 2)}; }'
                    else:
                        body += '\nreturn Result{r1, cc, 0u, 0u};'
                    outputs.append(body)
                name = f'f{count}'
                for mode, body in enumerate(outputs):
                    functions.append(f'Result {name}_{mode}(uint r2, uint r3, uint r4, uint cc) '
                                     '{ uint r1=0;\n' + body + '\n}')
                calls.append(f'check({name}_0, {name}_1);')
                count += 1
        source = r'''
#include <algorithm>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
using uint = uint32_t;
using ulong = uint64_t;
using std::min;
uint uaddCarry(uint a, uint b, uint &c) { uint v=a+b; c=v<a; return v; }
uint usubBorrow(uint a, uint b, uint &c) { c=a<b; return a-b; }
struct Result { uint a,b,c,d; bool operator==(Result r) const {
    return a==r.a && b==r.b && c==r.c && d==r.d; } };
uint64_t seed=88172645463325252ull, checks=0;
uint rnd() { seed ^= seed<<13; seed ^= seed>>7; seed ^= seed<<17; return uint(seed); }
using Fn=Result(*)(uint,uint,uint,uint);
void check(Fn ref, Fn got) {
    const uint edges[]={0,1,2,3,31,32,33,63,64,65,0x7fffffff,0x80000000,0xfffffffe,0xffffffff,0x72,0x40000000,0xd0000000};
    constexpr uint n=sizeof(edges)/sizeof(edges[0]);
    for (uint i=0; i<2000; ++i) {
        uint a=rnd(), b=rnd(), c=rnd(), carry=i&1;
        if(i<n*n) { a=edges[i%n]; b=edges[i/n]; c=edges[(i+3)%n]; }
        if (!(ref(a,b,c,carry)==got(a,b,c,carry))) {
            std::fprintf(stderr,"numeric mismatch at check %llu\n",(unsigned long long)checks);
            std::exit(1);
        }
        ++checks;
    }
}
'''
        source += '\n'.join(functions)
        source += '\nint main() {\n' + '\n'.join(calls)
        source += '\nprintf("Generated arithmetic: %llu checks, 0 mismatches\\n", (unsigned long long)checks); }\n'
        cpp = self.work / 'numeric.cpp'
        cpp.write_text(source)
        exe = self.work / 'numeric'
        run(os.environ.get('CXX', 'c++'), '-std=c++17', '-O1', '-fsanitize=undefined',
            '-fno-sanitize-recover=all', str(cpp), '-o', str(exe))
        print(run(str(exe)), end='', flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--base', default=BASE)
    args, rest = parser.parse_known_args()
    BASE = args.base
    unittest.main(argv=[__file__, *rest], verbosity=2)
