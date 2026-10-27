#!/usr/bin/env python3
"""Host regression probes of extracted production code; not a device rendering proof."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
ROOT = Path(os.environ.get('SURFACE_REPO', Path(__file__).resolve().parents[1]))

def extract(text, signature):
    start = text.index(signature); brace = text.index('{', start); depth = 1; end = brace + 1
    while depth:
        depth += (text[end] == '{') - (text[end] == '}'); end += 1
    return text[start:end]

class SurfaceRegression(unittest.TestCase):
    def test_same_java_surface_new_native_target(self):
        text = (ROOT/'src/runtime/platform/android.c').read_text()
        start = text.index('                                bool valid =')
        end = text.index('                                (*env)->DeleteLocalRef(env,', start)
        branch = text[start:end]
        source = '''#include <stdbool.h>
#include <stdint.h>
#include <assert.h>
#include <stdio.h>
typedef void* jobject;
#define JNI_TRUE 1
#define JNI_FALSE 0
struct JNI {int (*CallBooleanMethod)(void*,void*,void*);int (*IsSameObject)(void*,void*,void*);void (*DeleteGlobalRef)(void*,void*);void* (*NewGlobalRef)(void*,void*);};
static int generation=1, acquired, released;
int valid(void*a,void*b,void*c){return generation!=0;}
int same(void*a,void*b,void*c){return b==c;}
void del(void*a,void*b){} void* ref(void*a,void*b){return b;}
void ANativeWindow_release(void*w){released++;}
void* ANativeWindow_fromSurface(void*e,void*s){acquired++;return (void*)(uintptr_t)generation;}
static void* g_letsgo_surface;static void* g_letsgo_native_window;
void* get(void*surface){struct JNI api={valid,same,del,ref};struct JNI*p=&api;struct JNI**env=&p;void*isValid=(void*)1;void*win=0;
''' + branch + '''return win;}
int main(void){
 void*s=(void*)100;
 assert(get(s)==(void*)1);
 generation=2; // same Java object, invalid interval never polled
 assert(get(s)==(void*)2);
 assert(get(s)==(void*)2); // balanced re-acquisition, same target
 generation=0; assert(get(s)==0);
 generation=3;assert(get(s)==(void*)3);
 assert(acquired==released+1);
 printf("native generation 1->2->invalid->3, acquired=%d released=%d\\n",acquired,released);
}
'''
        with tempfile.TemporaryDirectory() as td:
            p=Path(td);(p/'probe.c').write_text(source)
            subprocess.run(['cc','-std=c11',str(p/'probe.c'),'-o',str(p/'probe')],check=True)
            result = subprocess.run([str(p/'probe')], capture_output=True, text=True)
            print(result.stdout, end='')
            self.assertEqual(result.returncode, 0, result.stderr)

    def test_guard_gesture_dispatch(self):
        text=(ROOT/'apk/java/com/letsgorecomp/DisplayGuard.java').read_text()
        method=extract(text,'    @Override public boolean onTouchEvent')
        method=method.replace('    @Override ', '    ').replace('android.view.MotionEvent', 'MotionEvent')
        click=extract(text,'    @Override public boolean performClick').replace('    @Override ', '    ').replace('super.performClick();','')
        with tempfile.TemporaryDirectory() as td:
            p=Path(td);(p/'android/view').mkdir(parents=True)
            (p/'android/view/MotionEvent.java').write_text('''package android.view;
public class MotionEvent {public static final int ACTION_DOWN=0,ACTION_UP=1,ACTION_MOVE=2,ACTION_CANCEL=3;
int a;float x,y;public MotionEvent(int a,float x,float y){this.a=a;this.x=x;this.y=y;}
public int getAction(){return a;} public int getActionMasked(){return a;}public float getX(){return x;}public float getY(){return y;}}
''')
            source='''import android.view.MotionEvent;
class android {static class util {static class Log {static void i(String tag,String text){}}}}
public class Probe {
 boolean visible=true, pressed, touching; int requests;
 static class Rect {boolean contains(float x,float y){return x>=0&&x<100&&y>=0&&y<100;}}
 Rect btn=new Rect();static class LetsGoActivity {static int requests; static void nativeResumeDisplay(){requests++;}}
 public void setPressed(boolean p){pressed=p;} public boolean isPressed(){return pressed;}
''' + method + click + '''
 boolean event(int a,float x,float y){return onTouchEvent(new MotionEvent(a,x,y));}
 void gesture(int end,float x,float y,int expected){int before=LetsGoActivity.requests;
 boolean target=event(0,50,50); if(target){event(2,x,y);event(end,x,y);}
 if(LetsGoActivity.requests-before!=expected)throw new AssertionError("DOWN/"+end+" requests="+(LetsGoActivity.requests-before)+" expected="+expected);}
 public static void main(String[]args){Probe p=new Probe();p.gesture(1,50,50,1);p.gesture(3,50,50,0);p.gesture(1,150,50,0);
 if(p.event(0,150,150))throw new AssertionError("outside DOWN consumed");
 if(p.event(1,50,50))throw new AssertionError("orphan UP consumed");
 p.visible=false;if(p.event(0,50,50))throw new AssertionError("hidden consumed");
 System.out.println("gesture tap=1 cancel=0 outside=0 orphan=0 hidden=0");}}
'''
            (p/'Probe.java').write_text(source)
            jdk=Path(os.environ.get('JAVA_HOME','/opt/data/work/perso/letsgo-recomp-android/outils/jdk/jdk-17.0.21+9'))/'bin'
            subprocess.run([str(jdk/'javac'),'-d',td,str(p/'android/view/MotionEvent.java'),str(p/'Probe.java')],check=True)
            result = subprocess.run([str(jdk/'java'),'-cp',td,'Probe'], capture_output=True, text=True)
            print(result.stdout, end='')
            self.assertEqual(result.returncode, 0, result.stderr)

if __name__=='__main__':unittest.main()
