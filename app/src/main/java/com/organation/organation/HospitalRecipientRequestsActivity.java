package com.organation.organation;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HospitalRecipientRequestsActivity extends AppCompatActivity {
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    
    private RecyclerView rvRequests;
    private LinearLayout llEmptyState;
    private TextView tvEmptyMessage;
    private RequestAdapter requestAdapter;
    private List<RequestModel> requestList;
    private List<RequestModel> allRequests; // Store all requests for filtering
    
    private String hospitalId = "";
    private String hospitalName = "";
    
    // Filter UI Components
    private Spinner spinnerStatusFilter;
    private EditText etRecipientNameFilter;
    private EditText etOrganTypeFilter;
    private EditText etDateFilter;
    private Button btnApplyFilters;
    private Button btnClearFilters;
    
    // Filter values
    private String filterStatus = "";
    private String filterRecipientName = "";
    private String filterOrganType = "";
    private String filterDate = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hospital_recipient_requests);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        
        if (mAuth.getCurrentUser() != null) {
            String userUid = mAuth.getCurrentUser().getUid();
            loadHospitalInfo(userUid);
        }
        
        initializeViews();
        setupRecyclerView();
    }

    private void loadHospitalInfo(String userUid) {
        // First get hospital ID from user document
        db.collection("users").document(userUid)
                .get()
                .addOnSuccessListener(userDocument -> {
                    if (userDocument.exists()) {
                        hospitalId = userDocument.getString("hospitalId");
                        if (hospitalId != null) {
                            loadHospitalDetails(hospitalId);
                        } else {
                            // Search for hospital by UID if not in user document
                            searchHospitalByUid(userUid);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error loading user info", Toast.LENGTH_SHORT).show();
                });
    }

    private void searchHospitalByUid(String userUid) {
        db.collection("hospitals")
                .whereEqualTo("userUid", userUid)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        hospitalId = document.getId();
                        Object hospitalNameObj = document.get(FieldPath.of("01]Hospital_Name"));
                        hospitalName = hospitalNameObj != null ? hospitalNameObj.toString() : "";
                        loadRequestsForHospital();
                        break;
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error finding hospital", Toast.LENGTH_SHORT).show();
                });
    }

    private void loadHospitalDetails(String hospitalId) {
        Log.d("HospitalRequests", "Loading hospital details for ID: " + hospitalId);
        db.collection("hospitals").document(hospitalId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Object hospitalNameObj = documentSnapshot.get(FieldPath.of("01]Hospital_Name"));
                        hospitalName = hospitalNameObj != null ? hospitalNameObj.toString() : "";
                        Log.d("HospitalRequests", "Hospital name loaded: '" + hospitalName + "'");
                        loadRequestsForHospital();
                    } else {
                        Log.w("HospitalRequests", "Hospital document not found for ID: " + hospitalId);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("HospitalRequests", "Error loading hospital details", e);
                    Toast.makeText(this, "Error loading hospital details", Toast.LENGTH_SHORT).show();
                });
    }

    private void initializeViews() {
        rvRequests = findViewById(R.id.rvRequests);
        llEmptyState = findViewById(R.id.llEmptyState);
        tvEmptyMessage = findViewById(R.id.tvEmptyMessage);
        
        // Initialize filter components
        spinnerStatusFilter = findViewById(R.id.spinnerStatusFilter);
        etRecipientNameFilter = findViewById(R.id.etRecipientNameFilter);
        etOrganTypeFilter = findViewById(R.id.etOrganTypeFilter);
        etDateFilter = findViewById(R.id.etDateFilter);
        btnApplyFilters = findViewById(R.id.btnApplyFilters);
        btnClearFilters = findViewById(R.id.btnClearFilters);
        
        // Set toolbar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Recipient Requests");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        
        // Setup status spinner
        setupStatusSpinner();
        
        // Setup filter button listeners
        setupFilterListeners();
    }

    private void setupRecyclerView() {
        requestList = new ArrayList<>();
        requestAdapter = new RequestAdapter(this, requestList, "hospital");
        
        rvRequests.setLayoutManager(new LinearLayoutManager(this));
        rvRequests.setAdapter(requestAdapter);
        
        // Set click listener for request items
        requestAdapter.setOnItemClickListener(position -> {
            RequestModel request = requestList.get(position);
            Intent intent = new Intent(this, RequestDetailsActivity.class);
            intent.putExtra("requestId", request.getRequestId());
            intent.putExtra("userType", "hospital");
            intent.putExtra("hospitalId", hospitalId);
            startActivity(intent);
        });
        
        // Set donor match listener for hospital users
        requestAdapter.setOnDonorMatchListener(request -> {
            convertRequestToRecipientWithAge(request, recipient -> {
                Intent intent = new Intent(this, DonorMatchingReportActivity.class);
                intent.putExtra("recipient", recipient);
                startActivity(intent);
            });
        });
    }

    private void loadRequestsForHospital() {
        Log.d("HospitalRequests", "Loading requests for hospital: '" + hospitalName + "'");
        
        if (hospitalName == null || hospitalName.isEmpty()) {
            Log.w("HospitalRequests", "Hospital name is null or empty");
            Toast.makeText(this, "Hospital information not found", Toast.LENGTH_SHORT).show();
            return;
        }

        // Load requests specifically for this hospital with real-time listener
        Log.d("HospitalRequests", "Querying organ_requests collection where hospitalName = '" + hospitalName + "'");
        db.collection("organ_requests")
                .whereEqualTo("hospitalName", hospitalName)
                .addSnapshotListener((queryDocumentSnapshots, e) -> {
                    if (e != null) {
                        Log.e("HospitalRequests", "Error loading requests", e);
                        Toast.makeText(this, "Error loading requests: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        updateEmptyState();
                        return;
                    }
                    
                    allRequests = new ArrayList<>(); // Initialize allRequests
                    requestList.clear();
                    
                    Log.d("HospitalRequests", "Real-time update: " + queryDocumentSnapshots.size() + " documents");
                    
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        Log.d("HospitalRequests", "Processing document: " + document.getId());
                        RequestModel request = documentToRequest(document);
                        if (request != null) {
                            Log.d("HospitalRequests", "Added request: " + request.getRecipientName() + " for " + request.getOrganType() + " (Status: " + request.getStatus() + ")");
                            allRequests.add(request); // Store all requests
                            requestList.add(request); // Display filtered requests
                        } else {
                            Log.w("HospitalRequests", "Failed to parse document: " + document.getId());
                        }
                    }
                    
                    Log.d("HospitalRequests", "Total requests in real-time update: " + requestList.size());
                    
                    // If no requests found with exact match, try fallback search
                    if (requestList.isEmpty()) {
                        Log.d("HospitalRequests", "No requests found with exact match, trying fallback search");
                        loadRequestsWithFallback();
                    } else {
                        // Apply current filters
                        applyFilters();
                        
                        // Show update message only for real changes (not initial load)
                        if (!queryDocumentSnapshots.getMetadata().isFromCache()) {
                            Toast.makeText(this, "Updated: " + requestList.size() + " requests for " + hospitalName, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }
    
    private void loadRequestsWithFallback() {
        // Try case-insensitive search by loading all requests and filtering client-side
        Log.d("HospitalRequests", "Loading all requests for client-side filtering");
        db.collection("organ_requests")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    allRequests = new ArrayList<>();
                    requestList.clear();
                    
                    String normalizedHospitalName = hospitalName.trim().toLowerCase();
                    
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        String docHospitalName = document.getString("hospitalName");
                        if (docHospitalName != null) {
                            String normalizedDocName = docHospitalName.trim().toLowerCase();
                            Log.d("HospitalRequests", "Comparing: '" + normalizedHospitalName + "' with '" + normalizedDocName + "'");
                            
                            if (normalizedHospitalName.equals(normalizedDocName)) {
                                Log.d("HospitalRequests", "Match found! Document hospitalName: '" + docHospitalName + "'");
                                RequestModel request = documentToRequest(document);
                                if (request != null) {
                                    allRequests.add(request);
                                    requestList.add(request);
                                    Log.d("HospitalRequests", "Added fallback request: " + request.getRecipientName());
                                }
                            }
                        }
                    }
                    
                    Log.d("HospitalRequests", "Fallback search found " + requestList.size() + " requests");
                    applyFilters();
                })
                .addOnFailureListener(e -> {
                    Log.e("HospitalRequests", "Error in fallback search", e);
                    applyFilters();
                });
    }

    private void sortRequests() {
        // Sort by status priority: pending > approved > processed > completed > declined
        // Then by date (newest first within each status)
        requestList.sort((r1, r2) -> {
            int statusCompare = getStatusPriority(r1.getStatus()) - getStatusPriority(r2.getStatus());
            if (statusCompare != 0) {
                return statusCompare;
            }
            return r2.getRequestDate().compareTo(r1.getRequestDate());
        });
    }

    private int getStatusPriority(String status) {
        switch (status) {
            case "pending": return 1;
            case "approved": return 2;
            case "processed": return 3;
            case "completed": return 4;
            case "declined": return 5;
            default: return 6;
        }
    }

    private void updateEmptyState() {
        if (requestList.isEmpty()) {
            llEmptyState.setVisibility(View.VISIBLE);
            rvRequests.setVisibility(View.GONE);
            tvEmptyMessage.setText("No recipient requests found for " + 
                    (hospitalName != null ? hospitalName : "your hospital") + ".\n\n" +
                    "Requests will appear here when recipients make organ requests at your hospital.");
        } else {
            llEmptyState.setVisibility(View.GONE);
            rvRequests.setVisibility(View.VISIBLE);
        }
    }

    private RequestModel documentToRequest(QueryDocumentSnapshot document) {
        try {
            RequestModel request = new RequestModel();
            
            // Safe string retrieval with null checks
            request.setRequestId(getSafeString(document, "requestId"));
            request.setRecipientUid(getSafeString(document, "recipientUid"));
            request.setRecipientName(getSafeString(document, "recipientName"));
            request.setRecipientAadhaar(getSafeString(document, "recipientAadhaar"));
            request.setOrganType(getSafeString(document, "organType"));
            request.setBloodType(getSafeString(document, "bloodType"));
            request.setUrgency(getSafeString(document, "urgency"));
            request.setHospitalName(getSafeString(document, "hospitalName"));
            request.setHospitalCity(getSafeString(document, "hospitalCity"));
            request.setHospitalLocation(getSafeString(document, "hospitalLocation"));
            request.setTreatingDoctor(getSafeString(document, "treatingDoctor"));
            request.setMedicalDetails(getSafeString(document, "medicalDetails"));
            request.setAdditionalNotes(getSafeString(document, "additionalNotes"));
            request.setRequestDate(getSafeString(document, "requestDate"));
            request.setStatus(getSafeString(document, "status"));
            request.setHospitalNotes(getSafeString(document, "hospitalNotes"));
            request.setApprovedDate(getSafeString(document, "approvedDate"));
            request.setProcessedDate(getSafeString(document, "processedDate"));
            request.setCompletedDate(getSafeString(document, "completedDate"));
            request.setDeclinedDate(getSafeString(document, "declinedDate"));
            request.setDeclinedReason(getSafeString(document, "declinedReason"));
            
            // Validate essential fields
            if (request.getRequestId() == null || request.getRequestId().isEmpty()) {
                return null;
            }
            
            return request;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private String getSafeString(QueryDocumentSnapshot document, String fieldName) {
        try {
            String value = document.getString(fieldName);
            return value != null ? value : "";
        } catch (Exception e) {
            return "";
        }
    }
    
    private String getSafeStringFromDocument(com.google.firebase.firestore.DocumentSnapshot document, String fieldName) {
        try {
            Object value = document.get(FieldPath.of(fieldName));
            return value != null ? value.toString() : "";
        } catch (Exception e) {
            return "";
        }
    }

    private void setupStatusSpinner() {
        String[] statusOptions = {"All Status", "pending", "approved", "processed", "completed", "declined"};
        ArrayAdapter<String> statusAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                statusOptions
        );
        spinnerStatusFilter.setAdapter(statusAdapter);
        spinnerStatusFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    filterStatus = ""; // All Status
                } else {
                    filterStatus = statusOptions[position];
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void setupFilterListeners() {
        btnApplyFilters.setOnClickListener(v -> applyFilters());
        
        btnClearFilters.setOnClickListener(v -> {
            clearFilters();
            applyFilters();
        });
    }

    private void clearFilters() {
        filterStatus = "";
        filterRecipientName = "";
        filterOrganType = "";
        filterDate = "";
        
        // Reset UI
        spinnerStatusFilter.setSelection(0);
        etRecipientNameFilter.setText("");
        etOrganTypeFilter.setText("");
        etDateFilter.setText("");
    }

    private void applyFilters() {
        if (allRequests == null) return;
        
        requestList.clear();
        
        for (RequestModel request : allRequests) {
            if (matchesFilters(request)) {
                requestList.add(request);
            }
        }
        
        // Sort filtered results
        sortRequests();
        
        // Update UI
        requestAdapter.notifyDataSetChanged();
        updateEmptyState();
    }

    private boolean matchesFilters(RequestModel request) {
        // Status filter
        if (!filterStatus.isEmpty() && !filterStatus.equals("All Status")) {
            if (!filterStatus.equals(request.getStatus())) {
                return false;
            }
        }
        
        // Recipient name filter
        if (!filterRecipientName.isEmpty()) {
            String recipientName = request.getRecipientName();
            if (recipientName == null || 
                !recipientName.toLowerCase().contains(filterRecipientName.toLowerCase())) {
                return false;
            }
        }
        
        // Organ type filter
        if (!filterOrganType.isEmpty()) {
            String organType = request.getOrganType();
            if (organType == null || 
                !organType.toLowerCase().contains(filterOrganType.toLowerCase())) {
                return false;
            }
        }
        
        // Date filter
        if (!filterDate.isEmpty()) {
            String requestDate = request.getRequestDate();
            if (requestDate == null || !requestDate.contains(filterDate)) {
                return false;
            }
        }
        
        return true;
    }

    private void convertRequestToRecipientWithAge(RequestModel request,
                                                  java.util.function.Consumer<RecipientModel> onComplete) {
        RecipientModel recipient = new RecipientModel();
        recipient.setFullName(request.getRecipientName());
        recipient.setAadhaarNo(request.getRecipientAadhaar());
        recipient.setBloodGroup(request.getBloodType());
        recipient.setOrgansNeeded(request.getOrganType());
        recipient.setUrgency(request.getUrgency());

        // Fetch age from Recepients collection using Aadhaar
        String aadhaar = request.getRecipientAadhaar();
        if (aadhaar != null && !aadhaar.isEmpty()) {
            db.collection("Recepients").document(aadhaar)
                    .get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists()) {
                            String age = doc.get(com.google.firebase.firestore.FieldPath.of("04]Age")) != null ?
                                    doc.get(com.google.firebase.firestore.FieldPath.of("04]Age")).toString() : null;
                            if (age != null) {
                                recipient.setAge(age);
                                Log.d("HospitalRequests", "Recipient age loaded: " + age);
                            }
                        }
                        loadCompleteHospitalDetails(request, recipient);
                        onComplete.accept(recipient);
                    })
                    .addOnFailureListener(e -> {
                        Log.e("HospitalRequests", "Failed to fetch recipient age", e);
                        loadCompleteHospitalDetails(request, recipient);
                        onComplete.accept(recipient);
                    });
        } else {
            loadCompleteHospitalDetails(request, recipient);
            onComplete.accept(recipient);
        }
    }
    
    private void loadCompleteHospitalDetails(RequestModel request, RecipientModel recipient) {
        try {
            // Get current hospital user's ID
            String currentUserUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
            
            // Load hospital data from Firestore
            db.collection("hospitals")
                .whereEqualTo("userId", currentUserUid)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        // Get the first hospital document
                        com.google.firebase.firestore.DocumentSnapshot hospitalDoc = queryDocumentSnapshots.getDocuments().get(0);
                        
                        Map<String, String> hospitalDetails = new HashMap<>();
                        
                        // Add all available hospital contact details
                        hospitalDetails.put("hospitalName", getSafeStringFromDocument(hospitalDoc, "01]Hospital_Name"));
                        hospitalDetails.put("contactNumber", getSafeStringFromDocument(hospitalDoc, "02]Contact_Number"));
                        hospitalDetails.put("officialEmail", getSafeStringFromDocument(hospitalDoc, "03]Official_Email"));
                        hospitalDetails.put("street", getSafeStringFromDocument(hospitalDoc, "04]Street"));
                        hospitalDetails.put("city", getSafeStringFromDocument(hospitalDoc, "05]City"));
                        hospitalDetails.put("state", getSafeStringFromDocument(hospitalDoc, "06]State"));
                        hospitalDetails.put("landmark", getSafeStringFromDocument(hospitalDoc, "07]Landmark"));
                        hospitalDetails.put("govRegNumber", getSafeStringFromDocument(hospitalDoc, "09]Gov_Reg_Number"));
                        hospitalDetails.put("authorityContact", getSafeStringFromDocument(hospitalDoc, "10]Authority_Contact"));
                        hospitalDetails.put("authorityEmail", getSafeStringFromDocument(hospitalDoc, "11]Authority_Email"));
                        hospitalDetails.put("hospitalType", getSafeStringFromDocument(hospitalDoc, "12]Hospital_Type"));
                        hospitalDetails.put("websiteUrl", getSafeStringFromDocument(hospitalDoc, "17]Website_URL"));
                        hospitalDetails.put("treatingDoctor", request.getTreatingDoctor());
                        hospitalDetails.put("hospitalLocation", request.getHospitalLocation());
                        
                        recipient.setHospitalDetails(hospitalDetails);
                        
                        Log.d("HospitalRequests", "Hospital details loaded: " + hospitalDetails.size() + " fields");
                    } else {
                        Log.w("HospitalRequests", "No hospital document found for user: " + currentUserUid);
                        // Fallback to basic details
                        Map<String, String> basicDetails = new HashMap<>();
                        basicDetails.put("hospitalName", request.getHospitalName());
                        basicDetails.put("treatingDoctor", request.getTreatingDoctor());
                        basicDetails.put("hospitalLocation", request.getHospitalLocation());
                        recipient.setHospitalDetails(basicDetails);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("HospitalRequests", "Error loading hospital details", e);
                    // Fallback to basic details
                    Map<String, String> basicDetails = new HashMap<>();
                    basicDetails.put("hospitalName", request.getHospitalName());
                    basicDetails.put("treatingDoctor", request.getTreatingDoctor());
                    basicDetails.put("hospitalLocation", request.getHospitalLocation());
                    recipient.setHospitalDetails(basicDetails);
                });
        } catch (Exception e) {
            Log.e("HospitalRequests", "Error in loadCompleteHospitalDetails", e);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
