# OrgaNation - Organ Donation & Hospital Management System

🏥 **A life-saving Android application** that revolutionizes organ donation management through intelligent matching algorithms, real-time hospital verification workflows, and secure multi-role authentication. Built with production-ready Firebase backend and modern Android architecture.

## 🚀 **Key Technical Achievements**

### 💡 **Innovation Highlights**
- **🧠 Intelligent Matching Algorithm**: Medical compatibility scoring with blood type, tissue typing, and urgency prioritization
- **🏥 Hospital Verification System**: Complete admin approval workflow with real-time status tracking
- **🔐 Multi-Role Security**: Role-based access control across 4 user types with Firebase security rules
- **📱 Real-Time Synchronization**: Live updates across all user types using Firestore listeners
- **🔔 Multi-Channel Notifications**: SMS, Email, and In-app notifications for critical medical updates

### 🎯 **Core Business Features**
- **👥 Multi-User Management**: Admin, Hospital, Donor, Recipient workflows with specialized dashboards
- **🏥 Hospital Network**: Multi-hospital system with approval workflows and data isolation
- **🔄 End-to-End Process**: Complete journey from registration to organ transplantation
- **📊 Analytics Dashboard**: Real-time statistics and reporting for admin oversight
- **🔒 Medical Data Security**: HIPAA-compliant data handling with encryption

### ⚡ **Technical Excellence**
- **🏗️ MVVM Architecture**: LiveData, ViewModel, and Repository pattern for scalable code
- **🔥 Firebase Integration**: Complex queries, indexing, and security rules implementation
- **🎨 Material Design**: Responsive UI with custom components and accessibility features
- **⚡ Performance Optimization**: Efficient data loading, pagination, and memory management

## Technology Stack

### Frontend
- **Platform**: Android (API 24+)
- **Language**: Java
- **Architecture**: MVVM with LiveData and ViewModel
- **UI Framework**: Material Design Components, RecyclerView, CardView
- **Navigation**: Navigation Component

### Backend
- **Database**: Firebase Firestore with complex queries and indexing
- **Authentication**: Firebase Authentication with role-based access
- **Storage**: Firebase Storage for medical documents
- **Notifications**: Firebase Cloud Messaging (FCM) for real-time alerts

## Project Architecture

### Key Components
- **Activities**: MainActivity, AdminRegistrationActivity, HospitalRegistrationActivity, DonorRegistrationActivity, RecipientRegistrationActivity
- **Adapters**: PendingHospitalAdapter, DonorAdapter, RecipientAdapter with custom ViewHolder patterns
- **Models**: Data models for Donor, Recipient, Hospital, and Admin entities
- **Firebase Integration**: Firestore collections with security rules and indexing

### Database Schema
```javascript
// Users Collection - Authentication and role management
{
  uid: string,
  email: string,
  userType: "Admin" | "Hospital" | "Donor" | "Recipient",
  isProfileComplete: boolean,
  createdAt: timestamp
}

// Admin Collection - Admin user profiles and management
{
  "01] Admin Name": string,
  "02] Contact No": string,
  "03] Email": string,
  "04] Gender": string,
  "05] Admin_Id": string,
  "06] Admin_Level": string,
  "07] Admin_Region": string,
  "08] GovIdNumber": string,
  "10] Username": string,
  "11] Password": string,
  "12] Security_Question": string,
  "13] Security_Answer": string,
  "14] registration_timestamp": timestamp,
  "15] isProfileComplete": boolean
}

// Hospitals Collection - Hospital management with verification
{
  "01]Hospital_Name": string,
  "02]Authority_Name": string,
  "03]Contact_Number": string,
  "04]Official_Email": string,
  "05]Street": string,
  "06]City": string,
  "07]State": string,
  "08]Landmark": string,
  "09]Gov_Reg_Number": string,
  "12]Hospital_Type": string,
  "18]Pincode": string,
  "21]Facilities": string,
  verificationStatus: "pending" | "approved" | "rejected",
  registrationDate: timestamp
}

// Donors Collection - Donor profiles with medical data
{
  donorId: string,
  hospitalId: string,
  personalInfo: object,
  medicalInfo: object,
  donationPreferences: array,
  availabilityStatus: string,
  registrationDate: timestamp
}

// Recepients Collection - Recipient profiles with medical requirements
{
  recipientId: string,
  hospitalId: string,
  personalInfo: object,
  medicalInfo: object,
  organRequirements: array,
  urgencyLevel: string,
  registrationDate: timestamp
}

// Organ_Requests Collection - Organ donation requests
{
  requestId: string,
  donorId: string,
  recipientId: string,
  hospitalId: string,
  organType: string,
  requestStatus: string,
  requestDate: timestamp,
  completionDate: timestamp
}

// Recipient_Requests Collection - Recipient organ requests
{
  requestId: string,
  recipientId: string,
  hospitalId: string,
  organType: string,
  urgencyLevel: string,
  requestStatus: string,
  requestDate: timestamp
}

// Notifications Collection - In-app notifications
{
  notificationId: string,
  userId: string,
  userType: string,
  title: string,
  message: string,
  notificationType: string,
  isRead: boolean,
  createdAt: timestamp
}

// Email_Notifications Collection - Email notification logs
{
  notificationId: string,
  recipientEmail: string,
  subject: string,
  message: string,
  notificationType: string,
  sentStatus: string,
  sentAt: timestamp
}

// Transplant_Notifications Collection - Transplant update notifications
{
  notificationId: string,
  donorId: string,
  recipientId: string,
  hospitalId: string,
  transplantStatus: string,
  message: string,
  createdAt: timestamp
}
```

## Installation & Setup

### Prerequisites
- Android Studio Arctic Fox or later
- Android SDK API 24+
- Firebase account for backend services

### Quick Setup
1. Clone the repository
2. Add `google-services.json` to `app/` folder
3. Configure Firebase services (Firestore, Auth, Storage)
4. Set up Firestore security rules
5. Build and run: `./gradlew assembleDebug`

## Key Technical Implementations

### Hospital Approval Workflow
- **Registration Flow**: Hospital registration with document upload
- **Admin Verification**: Pending hospital list with approve/reject functionality
- **Status Management**: Real-time status updates and notifications
- **Security**: Firestore security rules for role-based access

### Organ Matching Algorithm
- **Compatibility Scoring**: Medical data matching with weighted criteria
- **Urgency Prioritization**: Emergency cases prioritized in matching
- **Hospital Filtering**: Recipients matched with donor's registered hospital
- **Real-time Updates**: Live status tracking for donation process

### Data Management
- **Null Safety**: Comprehensive null checking with `getSafeString()` utility methods
- **Error Handling**: Firebase error handling with user-friendly messages
- **Performance**: Firestore indexing for efficient queries
- **Security**: Input validation and secure data storage

### UI/UX Features
- **Material Design**: Modern UI with Material Components
- **Responsive Layout**: Optimized for various screen sizes
- **Navigation**: Drawer navigation with role-based menu items
- **User Feedback**: Toast messages, dialogs, and loading indicators

## Security & Performance

### Firebase Security Rules
- Role-based access control for all collections
- Data validation and sanitization
- Secure document access patterns
- Audit logging for sensitive operations

### Performance Optimizations
- Firestore indexing for complex queries
- RecyclerView optimization with ViewHolder pattern
- Background task management
- Memory leak prevention with proper lifecycle management

---

**Built with modern Android development practices and Firebase backend for scalable organ donation management.**
