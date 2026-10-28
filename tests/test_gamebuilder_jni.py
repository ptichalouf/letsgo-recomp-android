"""Exercise production JNI fork/exec/cancel on host, real JVM and child processes."""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class Jni(unittest.TestCase):
    def test_utf8_exec_and_cancel_group(self):
        compiler = shutil.which('javac')
        assert compiler is not None
        javac = Path(compiler).resolve()
        jdk = javac.parents[1]
        with tempfile.TemporaryDirectory() as tmp:
            d = Path(tmp); package = d / 'com/letsgorecomp'; package.mkdir(parents=True)
            subprocess.run(['cc','-shared','-fPIC','-pthread','-Wall','-Wextra',
                '-I'+str(jdk/'include'),'-I'+str(jdk/'include/linux'),
                str(ROOT/'apk/gamebuild/jni.c'),'-o',str(d/'libgamebuilder.so')],check=True,capture_output=True)
            (d/'ok.py').write_text('import pathlib,sys;pathlib.Path(sys.argv[2],"passed").write_text(sys.argv[1])')
            (d/'slow.py').write_text('import subprocess,time;subprocess.Popen(["/bin/sleep","60"]);time.sleep(60)')
            source = package/'GameBuild.java'
            source.write_text('''package com.letsgorecomp;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
public class GameBuild {
 static {System.loadLibrary("gamebuilder");}
 static native int execute(byte[] a,byte[] b,byte[] c,byte[] d,byte[] e);
 static native void begin();static native void cancel();
 static byte[] b(String s){byte[] x=s.getBytes(StandardCharsets.UTF_8);return Arrays.copyOf(x,x.length+1);}
 public static void main(String[] a)throws Exception {
  begin();int ok=execute(b("/usr/bin/python3"),b(a[0]+"/ok.py"),b("Pokémon 🎮"),b(a[0]),b(a[0]));
  if(ok!=0)throw new AssertionError("exec="+ok);
  begin();Thread t=new Thread(()->{int r=execute(b("/usr/bin/python3"),b(a[0]+"/slow.py"),b(a[0]),b(a[0]),b(a[0]));if(r!=130)throw new AssertionError("cancel="+r);});
  t.start();Thread.sleep(600);cancel();t.join(3000);if(t.isAlive())throw new AssertionError("child survived");
  // Stop before execute cannot be erased by execute itself.
  begin();cancel();if(execute(b("/usr/bin/python3"),b(a[0]+"/ok.py"),b(a[0]),b(a[0]),b(a[0]))==0)throw new AssertionError("pre-cancel lost");
  System.out.println("JNI_EXEC_UTF8_CANCEL_OK");
 }
}''')
            subprocess.run([str(javac),str(source)],check=True,capture_output=True)
            result=subprocess.run([str(jdk/'bin/java'),'-Djava.library.path='+str(d),'-cp',str(d),'com.letsgorecomp.GameBuild',str(d)],capture_output=True,text=True,timeout=15)
            self.assertEqual(result.returncode,0,result.stdout+result.stderr)
            self.assertIn('JNI_EXEC_UTF8_CANCEL_OK',result.stdout)
            self.assertEqual((d/'passed').read_text(),'Pokémon 🎮')
            # No compiler group is left running; sleep parent was group-killed.
            procs=subprocess.check_output(['ps','-eo','args'],text=True)
            self.assertNotIn('/usr/bin/python3 '+str(d)+'/slow.py',procs)


if __name__ == '__main__': unittest.main()
