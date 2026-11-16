import com.android.apksig.ApkVerifier;

import java.io.File;
import java.util.List;

/** Verifies an APK with APK Signature Scheme v4 using its sidecar .idsig file. */
public final class V4Verifier {

    public static void main(String[] args) throws Exception {
        File apk = new File(args[0]);
        File idsig = new File(args[1]);

        ApkVerifier.Result result = new ApkVerifier.Builder(apk)
                .setV4SignatureFile(idsig)
                        // Check from API 24 upward so the v2 signature scheme is exercised too
                        // (from API 31 on, v3 supersedes v2 and apksigner reports v2 as unused).
                        .setMinCheckedPlatformVersion(24)
                .build()
                .verify();

        System.out.println("APK: " + apk.getAbsolutePath());
        System.out.println("idsig: " + idsig.getAbsolutePath());
        System.out.println("v1: " + result.isVerifiedUsingV1Scheme());
        System.out.println("v2: " + result.isVerifiedUsingV2Scheme());
        System.out.println("v3: " + result.isVerifiedUsingV3Scheme());
        System.out.println("v4: " + result.isVerifiedUsingV4Scheme());

        if (!result.isVerified()) {
            for (ApkVerifier.IssueWithParams issue : result.getErrors()) {
                System.out.println("ERROR " + issue);
            }
            System.exit(1);
        }

        List<ApkVerifier.Result.V4SchemeSignerInfo> v4Signers = result.getV4SchemeSigners();
        if (v4Signers != null) {
            for (ApkVerifier.Result.V4SchemeSignerInfo signer : v4Signers) {
                System.out.println("v4 signer: " + signer.getCertificate().getSubjectDN());
            }
        }
    }
}