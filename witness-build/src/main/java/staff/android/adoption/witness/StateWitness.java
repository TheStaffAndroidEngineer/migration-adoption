package staff.android.adoption.witness;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Bundle;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.json.JSONObject;

/** Same-signer lab inspector, no dependency on target app classes or R8 names.
 * Reads only the primary file; it does not invoke AtomicFile recovery or write.
 * Running instrumentation may terminate the target. Reopen and reacquire UI. */
public final class StateWitness extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Context target = getTargetContext();
            File file = new File(target.getFilesDir(), "cart-v1.txt");
            boolean exists = file.exists();
            if (exists && !file.isFile()) throw new IllegalStateException("Cart record is not a file");
            String record = exists ? new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8) : null;
            PackageInfo info = target.getPackageManager().getPackageInfo(target.getPackageName(), 0);
            JSONObject report = new JSONObject().put("exists", exists)
                .put("record", record == null ? JSONObject.NULL : record)
                .put("versionCode", Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode)
                .put("package", target.getPackageName()).put("osApi", Build.VERSION.SDK_INT)
                .put("scope", "Read-only primary file; instrumentation may restart the target");
            result.putString("installed_state", report.toString());
            finish(Activity.RESULT_OK, result);
        } catch (Exception error) {
            result.putString("failure", error.getClass().getName() + ": " + error.getMessage());
            finish(Activity.RESULT_CANCELED, result);
        }
    }
}
