"""Exercise the actual diagnostic gate and render-thread injection without a GPU."""
from pathlib import Path
import subprocess
import tempfile
import unittest
from test_surface_recovery import extract

ROOT = Path(__file__).resolve().parents[1]

class Injection(unittest.TestCase):
    def test_strict_android_gate(self):
        text = (ROOT / 'apk/java/com/letsgorecomp/LetsGoActivity.java').read_text()
        gate = extract(text, 'static boolean forceDisplayStalled')
        self.assertIn('"1".equals(diagProperty)', gate)
        self.assertIn('"1".equals(forceProperty)', gate)
        self.assertIn('Os.unsetenv("LARECOMP_FORCE_DISPLAY_STALLED")', text)
        self.assertIn('"debug.larecomp.force_display_stalled", "0"', text)
        with tempfile.TemporaryDirectory() as td:
            p=Path(td)
            (p/'Gate.java').write_text('public class Gate {'+gate+'''
public static void main(String[] args) {
 String[] values={null,"","0","1","true"," 1"};
 for(String a:values)for(String b:values)
  if(forceDisplayStalled(a,b)!=("1".equals(a)&&"1".equals(b)))throw new AssertionError();
 System.out.println("GATE_OK 36 combinations");
}}''')
            import os
            jdk=Path(os.environ.get('JAVA_HOME','/opt/data/work/perso/letsgo-recomp-android/outils/jdk/jdk-17.0.21+9'))/'bin'
            subprocess.run([str(jdk/'javac'),str(p/'Gate.java')],check=True)
            subprocess.run([str(jdk/'java'),'-cp',td,'Gate'],check=True)

    def test_render_thread_hold_and_resume(self):
        text = (ROOT / 'src/runtime/nvn/vulkan/device.c').read_text()
        method = extract(text, 'static bool force_display_stalled')
        source = '''#include <stdbool.h>
#include <stdlib.h>
#include <string.h>
#include <stdatomic.h>
#include <stdint.h>
#include <assert.h>
#include <stdio.h>
typedef uint32_t Uint32;
static Uint32 now = 100;
static atomic_uint g_last_present_ms;
static int dead;
Uint32 SDL_GetTicks(void){return now;}
void display_dead(void){dead++;}
#define LOG(...) printf(__VA_ARGS__)
''' + method + '''
int main(int argc,char**argv){
 bool enabled=argc>1;
 if(enabled)setenv("LARECOMP_FORCE_DISPLAY_STALLED","1",1);
 else unsetenv("LARECOMP_FORCE_DISPLAY_STALLED");
 assert(!force_display_stalled(false)); // never block startup
 atomic_store(&g_last_present_ms,100);
 assert(force_display_stalled(false)==enabled);
 now+=9000;
 assert(force_display_stalled(false)==enabled); // survives watchdog threshold
 assert(!force_display_stalled(true)); // manual rebuild releases hold
 assert(!force_display_stalled(false)); // one shot, cannot immediately stall again
 assert(dead==(enabled?2:0));
 puts("INJECTION_OK");
}
'''
        with tempfile.TemporaryDirectory() as td:
            src=Path(td)/'probe.c'; exe=Path(td)/'probe'
            src.write_text(source)
            subprocess.run(['cc','-std=c11','-D_POSIX_C_SOURCE=200809L',str(src),'-o',str(exe)],check=True)
            for args in ([],['enabled']):
                r=subprocess.run([str(exe),*args],capture_output=True,text=True)
                self.assertEqual(r.returncode,0,r.stdout+r.stderr)
                print(r.stdout,end='')

if __name__=='__main__': unittest.main()
