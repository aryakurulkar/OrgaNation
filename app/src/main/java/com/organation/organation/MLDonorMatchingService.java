package com.organation.organation;

import android.content.Context;
import android.util.Log;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * MLDonorMatchingService  –  ML-powered version
 * ─────────────────────────────────────────────
 * Flow:
 *   1. Fetch all donors from Firestore  (same as before)
 *   2. Serialise recipient + donors into JSON
 *   3. POST to Flask /predict endpoint
 *   4. Deserialise response → List<DonorMatchResult>
 *   5. Call back on the main thread
 *
 * The public API (DonorMatchingCallback, DonorMatchResult, findTopCompatibleDonors)
 * is IDENTICAL to the old file, so DonorMatchAdapter and
 * DonorMatchingReportActivity need zero changes.
 */
public class MLDonorMatchingService {

    private static final String TAG = "MLDonorMatching";

    // ─── CHANGE THIS to your computer's LAN IP while running Flask ───────
    // Example: "http://192.168.1.42:5000"
    // Find your IP on Windows: run  ipconfig  → look for IPv4 Address
    // Find your IP on Mac/Linux: run  ifconfig  or  ip addr
    private static final String FLASK_BASE_URL = "http://192.168.1.103:5000";
    // ─────────────────────────────────────────────────────────────────────

    private static final String PREDICT_ENDPOINT = FLASK_BASE_URL + "/predict";
    private static final int    TIMEOUT_MS        = 30_000; // 30 s

    private final FirebaseFirestore    db;
    private final ExecutorService      executor;

    // ── Public interface (unchanged) ──────────────────────────────────────
    public interface DonorMatchingCallback {
        void onMatchingCompleted(List<DonorMatchResult> topDonors);
        void onMatchingFailed(String error);
    }

    public static class DonorMatchResult {
        public DonorModel donor;
        public double     compatibilityScore;

        public DonorMatchResult(DonorModel donor, double compatibilityScore) {
            this.donor              = donor;
            this.compatibilityScore = compatibilityScore;
        }
    }

    // ── Constructor ───────────────────────────────────────────────────────
    public MLDonorMatchingService(Context context) {
        db       = FirebaseFirestore.getInstance();
        executor = Executors.newSingleThreadExecutor();
    }

    // ── Main entry point (unchanged signature) ────────────────────────────
    public void findTopCompatibleDonors(RecipientModel recipient,
                                        DonorMatchingCallback callback) {

        db.collection("donors")
                .get()
                .addOnSuccessListener(snapshots -> {

                    // 1. Collect donors from Firestore
                    List<DonorModel> donors = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        try {
                            DonorModel d = convertDocumentToDonor(doc);
                            if (d != null) donors.add(d);
                        } catch (Exception e) {
                            Log.e(TAG, "Error converting donor doc", e);
                        }
                    }

                    Log.d(TAG, "Fetched " + donors.size() + " donors from Firestore");

                    // 2. Call the ML API on a background thread
                    executor.execute(() -> {
                        try {
                            List<DonorMatchResult> results =
                                    callMlApi(recipient, donors);

                            // 3. Return on main thread
                            android.os.Handler mainHandler =
                                    new android.os.Handler(android.os.Looper.getMainLooper());
                            mainHandler.post(() -> callback.onMatchingCompleted(results));

                        } catch (Exception e) {
                            Log.e(TAG, "ML API call failed", e);
                            android.os.Handler mainHandler =
                                    new android.os.Handler(android.os.Looper.getMainLooper());
                            mainHandler.post(() -> callback.onMatchingFailed(e.getMessage()));
                        }
                    });
                })
                .addOnFailureListener(e ->
                        callback.onMatchingFailed("Firestore error: " + e.getMessage()));
    }

    // ── Build JSON, POST to Flask, parse response ─────────────────────────
    private List<DonorMatchResult> callMlApi(RecipientModel recipient,
                                             List<DonorModel> donors) throws Exception {

        // ── Build request JSON ────────────────────────────────────────────
        JSONObject requestBody = new JSONObject();

        // Recipient object
        JSONObject recipientJson = new JSONObject();
        recipientJson.put("name",            safeStr(recipient.getFullName()));
        recipientJson.put("age",             safeInt(recipient.getAge()));
        recipientJson.put("blood_group",     safeStr(recipient.getBloodGroup()).replace("\u2212", "-").replace("\u2013", "-"));
        recipientJson.put("required_organ",  safeStr(recipient.getOrgansNeeded()));
        requestBody.put("recipient", recipientJson);

        // Donors array
        JSONArray donorsArray = new JSONArray();
        for (DonorModel d : donors) {
            JSONObject dj = new JSONObject();
            dj.put("fullName",       safeStr(d.getFullName()));
            dj.put("age",            safeStr(d.getAge()));
            dj.put("bloodGroup",     safeStr(d.getBloodGroup()));
            dj.put("organsToDonate", safeStr(d.getOrgansToNDonate()));
            dj.put("gender",         safeStr(d.getGender()));
            dj.put("height",         safeStr(d.getHeight()));
            dj.put("weight",         safeStr(d.getWeight()));
            dj.put("phone",          safeStr(d.getPhone()));
            dj.put("email",          safeStr(d.getEmail()));
            dj.put("city",           safeStr(d.getCity()));
            dj.put("state",          safeStr(d.getState()));
            dj.put("street",         safeStr(d.getStreet()));
            dj.put("landmark",       safeStr(d.getLandmark()));
            dj.put("aadhaarNo",      safeStr(d.getAadhaarNo()));
            donorsArray.put(dj);
        }
        requestBody.put("donors", donorsArray);

        Log.d(TAG, "Sending " + donors.size() + " donors to ML API");

        // ── HTTP POST ─────────────────────────────────────────────────────
        URL url = new URL(PREDICT_ENDPOINT);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setDoOutput(true);
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);

        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = requestBody.toString().getBytes("utf-8");
            os.write(input, 0, input.length);
        }

        int responseCode = conn.getResponseCode();
        Log.d(TAG, "Flask API response code: " + responseCode);

        if (responseCode != 200) {
            throw new Exception("Flask API returned HTTP " + responseCode);
        }

        // ── Read response ─────────────────────────────────────────────────
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), "utf-8"))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        }

        String responseStr = sb.toString();
        Log.d(TAG, "Flask response: " + responseStr);

        // ── Parse response → DonorMatchResult list ────────────────────────
        JSONObject response     = new JSONObject(responseStr);
        JSONArray  topMatchesJson = response.optJSONArray("top_matches");

        List<DonorMatchResult> results = new ArrayList<>();
        if (topMatchesJson == null) return results;

        for (int i = 0; i < topMatchesJson.length(); i++) {
            JSONObject m = topMatchesJson.getJSONObject(i);

            DonorModel donor = new DonorModel();
            donor.setFullName      (m.optString("fullName",       ""));
            donor.setAge           (m.optString("age",            ""));
            donor.setBloodGroup    (m.optString("bloodGroup",     ""));
            donor.setOrgansToNDonate(m.optString("organsToDonate",""));
            donor.setGender        (m.optString("gender",         ""));
            donor.setHeight        (m.optString("height",         ""));
            donor.setWeight        (m.optString("weight",         ""));
            donor.setPhone         (m.optString("phone",          ""));
            donor.setEmail         (m.optString("email",          ""));
            donor.setCity          (m.optString("city",           ""));
            donor.setState         (m.optString("state",          ""));
            donor.setStreet        (m.optString("street",         ""));
            donor.setLandmark      (m.optString("landmark",       ""));
            donor.setAadhaarNo     (m.optString("aadhaarNo",      ""));

            double score = m.optDouble("compatibilityScore", 0.0);
            results.add(new DonorMatchResult(donor, score));
        }

        Log.d(TAG, "ML API returned " + results.size() + " top matches");
        return results;
    }

    // ── Firestore document → DonorModel (unchanged from original) ─────────
    private DonorModel convertDocumentToDonor(QueryDocumentSnapshot document) {
        try {
            Map<String, Object> data = document.getData();
            if (data == null) return null;

            DonorModel donor = new DonorModel();
            donor.setFullName      (getStringFromData(data, "01]Full_name"));
            donor.setAge           (getStringFromData(data, "04]Age"));
            donor.setWeight        (getStringFromData(data, "08]Weight"));
            donor.setHeight        (getStringFromData(data, "07]Height"));
            donor.setBloodGroup    (getStringFromData(data, "06]Blood_group"));
            donor.setGender        (getStringFromData(data, "05]Gender"));
            donor.setPhone         (getStringFromData(data, "09]Phone"));
            donor.setEmail         (getStringFromData(data, "10]Email"));
            donor.setCity          (getStringFromData(data, "12]City"));
            donor.setState         (getStringFromData(data, "11]State"));
            donor.setStreet        (getStringFromData(data, "13]Street"));
            donor.setLandmark      (getStringFromData(data, "14]Landmark"));
            donor.setOrgansToNDonate(getStringFromData(data, "16]Organs_to_donate"));
            donor.setAadhaarNo     (getStringFromData(data, "02]Aadhaar_no"));
            return donor;

        } catch (Exception e) {
            Log.e(TAG, "Error converting document to donor", e);
            return null;
        }
    }

    // ── Utility helpers ───────────────────────────────────────────────────
    private String getStringFromData(Map<String, Object> data, String key) {
        Object v = data.get(key);
        if (v instanceof String) return (String) v;
        return v != null ? v.toString() : "";
    }

    private String safeStr(String s) {
        return s != null ? s : "";
    }

    private int safeInt(String s) {
        try {
            if (s == null || s.trim().isEmpty()) return 0;
            // Remove any non-numeric characters except digits
            String cleaned = s.trim().replaceAll("[^0-9]", "");
            return cleaned.isEmpty() ? 0 : Integer.parseInt(cleaned);
        }
        catch (Exception e) { return 0; }
    }
}