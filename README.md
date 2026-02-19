# FairShare

**FairShare** is a web application designed to help users easily manage shared expenses with friends, roommates, or teams. It allows creating groups, tracking expenses, calculating balances, and generating reports so that everyone knows who owes whom.

---

##  Project Overview

- **Frontend:** React.js  
- **Backend:** Spring Boot (Java)  
- **Database:** MySQL 
- **Purpose:** Simplify splitting expenses, keeping track of payments, and maintaining a clear balance summary for all group members.  

---

##  Sprint 1 Features

Sprint 1 focused on the core functionality for initial release:

1. **User Authentication**
   - Signup and Login
   - Password validation and notifications for incorrect credentials

2. **Dashboard**
   - Shows user's net balance, what they owe, and what they are owed
   - Visual representation of balances with graphs

3. **Groups**
   - Create and join groups using an invite link
   - View existing groups
   - Admin features: rename group, delete group
   - Group details: 
     - Total spending  
     - Category-wise expense breakdown  
     - Expense history with "Paid by" information  
     - Balance summary showing all members

4. **Expense Management**
   - Add new expenses with: title, date, category, amount, notes, and participants
   - Edit and delete expenses (if admin or owner)
   - Automatic balance calculations per member

5. **Reports**
   - Download group expense report as CSV

---

## How to Run the Project

### 1. Clone the repository
```bash
git clone https://github.com/depaulcdm/course-project-fairshare.git
cd course-project-fairshare

````
### 2. Backend
- Open backend folder in your IDE (e.g., IntelliJ or VS Code)
- Configure application properties (application.properties) with your DB credentials
- Build and run the Spring Boot application: ./mvnw spring-boot:run
- The backend will run at http://localhost:8080

   ---
### 3. Frontend
- Open frontend folder in terminal
- Install dependencies: npm install
- Start the development server: npm start
- The frontend will run at http://localhost:5173

   ---
### Team 
- Khushi Shah
- Khush Prajapati
- Saquibuddin Syed




