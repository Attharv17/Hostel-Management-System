import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import javax.swing.JOptionPane;

/**
 * Manages the connection to the MySQL database.
 * Creates all necessary tables if they don't exist.
 */
public class DatabaseManager {

    // --- !!! IMPORTANT !!! ---
    // Update these values to match your MySQL server configuration.
    private static final String DB_HOST = "localhost";
    private static final String DB_PORT = "3306";
    private static final String DB_NAME = "hostel_db";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "Pravar@161106";

    // Full connection string
    private static final String DB_URL = "jdbc:mysql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME;

    /**
     * Gets a new connection to the database.
     * The caller is responsible for closing the connection.
     * @return A Connection object
     * @throws SQLException
     */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
    }

    /**
     * Initializes the database by creating all tables if they do not exist.
     * This method should be called once when the application starts.
     */
    public static void initializeDatabase() {
        // SQL for creating tables

        // Use VARCHAR(255) for standard strings, TEXT for long reasons
        // Use DATE for dates, VARCHAR(5) for HH:MM time
        // `AUTO_INCREMENT` creates a unique ID for each request

        String createStudentsTable = "CREATE TABLE IF NOT EXISTS students ("
                + "name VARCHAR(255) NOT NULL,"
                + "roll_number VARCHAR(50) PRIMARY KEY,"
                + "course VARCHAR(100),"
                + "year VARCHAR(20),"
                + "mobile VARCHAR(20),"
                + "status VARCHAR(50) DEFAULT 'Active',"
                + "room_number VARCHAR(20) DEFAULT 'Not Allocated',"
                + "username VARCHAR(100) NOT NULL UNIQUE,"
                + "password VARCHAR(255) NOT NULL"
                + ");";

        String createRoomsTable = "CREATE TABLE IF NOT EXISTS rooms ("
                + "room_number VARCHAR(20) PRIMARY KEY,"
                + "type VARCHAR(50) NOT NULL,"
                + "capacity INT NOT NULL,"
                + "is_available BOOLEAN DEFAULT TRUE"
                + ");";

        String createMessTable = "CREATE TABLE IF NOT EXISTS mess_attendance ("
                + "student_roll VARCHAR(50) NOT NULL,"
                + "attendance_date DATE NOT NULL,"
                + "is_attending BOOLEAN DEFAULT FALSE,"
                + "PRIMARY KEY (student_roll, attendance_date),"
                + "FOREIGN KEY (student_roll) REFERENCES students(roll_number) ON DELETE CASCADE"
                + ");";

        String createCleaningTable = "CREATE TABLE IF NOT EXISTS cleaning_reports ("
                + "student_roll VARCHAR(50) NOT NULL,"
                + "report_date DATE NOT NULL,"
                + "is_cleaned BOOLEAN DEFAULT FALSE,"
                + "PRIMARY KEY (student_roll, report_date),"
                + "FOREIGN KEY (student_roll) REFERENCES students(roll_number) ON DELETE CASCADE"
                + ");";

        String createLeaveTable = "CREATE TABLE IF NOT EXISTS leave_requests ("
                + "id INT AUTO_INCREMENT PRIMARY KEY,"
                + "student_roll VARCHAR(50) NOT NULL,"
                + "start_date DATE NOT NULL,"
                + "end_date DATE NOT NULL,"
                + "reason TEXT NOT NULL,"
                + "status VARCHAR(20) DEFAULT 'Pending',"
                + "FOREIGN KEY (student_roll) REFERENCES students(roll_number) ON DELETE CASCADE"
                + ");";

        String createOutpassTable = "CREATE TABLE IF NOT EXISTS outpass_requests ("
                + "id INT AUTO_INCREMENT PRIMARY KEY,"
                + "student_roll VARCHAR(50) NOT NULL,"
                + "outpass_date DATE NOT NULL,"
                + "time_out VARCHAR(5) NOT NULL," // HH:MM
                + "time_in VARCHAR(5) NOT NULL,"  // HH:MM
                + "reason TEXT NOT NULL,"
                + "status VARCHAR(20) DEFAULT 'Pending',"
                + "FOREIGN KEY (student_roll) REFERENCES students(roll_number) ON DELETE CASCADE"
                + ");";

        // --- NEW TABLE for Food Wastage ---
        String createMetricsTable = "CREATE TABLE IF NOT EXISTS hostel_metrics ("
                + "metric_key VARCHAR(50) PRIMARY KEY,"
                + "metric_value DOUBLE NOT NULL DEFAULT 0.0"
                + ");";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            // Execute all table creation queries
            stmt.executeUpdate(createStudentsTable);
            stmt.executeUpdate(createRoomsTable);
            stmt.executeUpdate(createMessTable);
            stmt.executeUpdate(createCleaningTable);
            stmt.executeUpdate(createLeaveTable);
            stmt.executeUpdate(createOutpassTable);
            stmt.executeUpdate(createMetricsTable);

            // --- NEW: Insert default metrics if they don't exist ---
            // `INSERT IGNORE` will not insert if the key already exists
            stmt.executeUpdate("INSERT IGNORE INTO hostel_metrics (metric_key, metric_value) VALUES ('this_month_wastage', 0.0)");
            stmt.executeUpdate("INSERT IGNORE INTO hostel_metrics (metric_key, metric_value) VALUES ('last_month_wastage', 0.0)");

            System.out.println("Database tables checked/created successfully.");

        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null,
                    "Database Connection Failed! Please check your connection and credentials.\nError: " + e.getMessage(),
                    "Database Error",
                    JOptionPane.ERROR_MESSAGE);
            System.exit(1); // Exit if DB connection fails on start
        }
    }
}

