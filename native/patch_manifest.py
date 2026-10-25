p = 'android/app/src/main/AndroidManifest.xml'
s = open(p).read()
names = ['ACCESS_FINE_LOCATION', 'ACCESS_COARSE_LOCATION', 'WAKE_LOCK', 'ACTIVITY_RECOGNITION',
         'FOREGROUND_SERVICE', 'FOREGROUND_SERVICE_HEALTH', 'POST_NOTIFICATIONS', 'RECEIVE_BOOT_COMPLETED']
perms = ''.join('    <uses-permission android:name="android.permission.%s" />\n' % n for n in names)
perms += '    <uses-feature android:name="android.hardware.location.gps" android:required="false" />\n'
perms += '    <uses-feature android:name="android.hardware.sensor.stepcounter" android:required="false" />\n'
comps = '''        <service android:name="com.walktracker.app.StepService" android:exported="false" android:foregroundServiceType="health" />
        <receiver android:name="com.walktracker.app.BootReceiver" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
                <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
            </intent-filter>
        </receiver>
    '''
s = s.replace('<application', perms + '    <application', 1).replace('</application>', comps + '</application>', 1)
open(p, 'w').write(s)
print(s)
