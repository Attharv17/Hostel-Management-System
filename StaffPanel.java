import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * The main staff portal, containing all admin tabs.
 * Includes controls for managing food wastage metrics.
 */
public class StaffPanel extends JPanel {

    private MainFrame mainFrame;
    private Hostel hostel;
    private DashboardPanel dashboardPanel; // Kept for potential future use

    // --- Components for Admin Controls ---
    private JTextField thisMonthWastageField;
    private JTextField lastMonthWastageField;
    private JButton saveWastageButton;

    // Constructor updated to accept MainFrame for logout
    public StaffPanel(Hostel hostel, DashboardPanel dashboardPanel, MainFrame mainFrame) {
        this.hostel = hostel;
        this.dashboardPanel = dashboardPanel;
        this.mainFrame = mainFrame;

        setLayout(new BorderLayout());
        setBackground(new Color(230, 230, 230));

        JTabbedPane staffTabbedPane = new JTabbedPane();
        staffTabbedPane.setFont(new Font("Segoe UI", Font.BOLD, 14));

        // Management Tabs
        staffTabbedPane.addTab("Manage Rooms", new RoomManagementPanel(hostel));
        staffTabbedPane.addTab("Manage Students", new StudentManagementPanel(hostel));
        staffTabbedPane.addTab("Room Allocation", new RoomAllocationPanel(hostel));

        // New Daily Status Tab
        staffTabbedPane.addTab("Daily Status", new DailyStatusPanel());

        // Admin Controls Tab
        staffTabbedPane.addTab("Admin Controls", createAdminControlsTab());

        add(staffTabbedPane, BorderLayout.CENTER);
    }

    /**
     * Creates the Admin Controls panel with food wastage editor and logout.
     */
    private JPanel createAdminControlsTab() {
        JPanel mainPanel = new JPanel(new BorderLayout(20, 20));
        mainPanel.setBorder(new EmptyBorder(20, 20, 20, 20));

        // --- Food Wastage Panel ---
        JPanel wastagePanel = new JPanel(new GridBagLayout());
        wastagePanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(150, 150, 150)),
                "Food Wastage Metrics (in kg)",
                javax.swing.border.TitledBorder.LEFT, javax.swing.border.TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 14))
        );
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // This Month
        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.EAST;
        wastagePanel.add(new JLabel("This Month's Wastage:"), gbc);
        gbc.gridx = 1; gbc.gridy = 0; gbc.anchor = GridBagConstraints.WEST;
        thisMonthWastageField = new JTextField(10);
        wastagePanel.add(thisMonthWastageField, gbc);

        // Last Month
        gbc.gridx = 0; gbc.gridy = 1; gbc.anchor = GridBagConstraints.EAST;
        wastagePanel.add(new JLabel("Last Month's Wastage:"), gbc);
        gbc.gridx = 1; gbc.gridy = 1; gbc.anchor = GridBagConstraints.WEST;
        lastMonthWastageField = new JTextField(10);
        wastagePanel.add(lastMonthWastageField, gbc);

        // Save Button
        gbc.gridx = 1; gbc.gridy = 2; gbc.anchor = GridBagConstraints.WEST;
        saveWastageButton = new JButton("Save Wastage Data");
        saveWastageButton.addActionListener(e -> saveWastageData());
        wastagePanel.add(saveWastageButton, gbc);

        mainPanel.add(wastagePanel, BorderLayout.NORTH);

        // --- Logout Panel ---
        JPanel logoutPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton logoutButton = new JButton("Logout (Back to User Selection)");
        logoutButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
        logoutButton.setBackground(new Color(231, 76, 60)); // Red color
        logoutButton.setForeground(Color.WHITE);
        logoutButton.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(
                    mainFrame,
                    "Are you sure you want to logout?",
                    "Logout Confirmation",
                    JOptionPane.YES_NO_OPTION);

            if (confirm == JOptionPane.YES_OPTION) {
                mainFrame.showUserSelection();
            }
        });
        logoutPanel.add(logoutButton);
        mainPanel.add(logoutPanel, BorderLayout.SOUTH);

        // Load initial data
        loadWastageData();

        return mainPanel;
    }

    /**
     * Loads the current wastage values from the DB into the text fields.
     */
    private void loadWastageData() {
        String sql = "SELECT metric_key, metric_value FROM hostel_metrics WHERE metric_key IN ('this_month_wastage', 'last_month_wastage')";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                if (rs.getString("metric_key").equals("this_month_wastage")) {
                    thisMonthWastageField.setText(String.valueOf(rs.getDouble("metric_value")));
                } else if (rs.getString("metric_key").equals("last_month_wastage")) {
                    lastMonthWastageField.setText(String.valueOf(rs.getDouble("metric_value")));
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Could not load wastage data: " + ex.getMessage(), "DB Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Saves the values from the text fields back to the database.
     */
    private void saveWastageData() {
        try {
            double thisMonth = Double.parseDouble(thisMonthWastageField.getText());
            double lastMonth = Double.parseDouble(lastMonthWastageField.getText());

            String sql = "UPDATE hostel_metrics SET metric_value = ? WHERE metric_key = ?";

            try (Connection conn = DatabaseManager.getConnection()) {
                // Update "this_month_wastage"
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setDouble(1, thisMonth);
                    pstmt.setString(2, "this_month_wastage");
                    pstmt.executeUpdate();
                }

                // Update "last_month_wastage"
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setDouble(1, lastMonth);
                    pstmt.setString(2, "last_month_wastage");
                    pstmt.executeUpdate();
                }

                JOptionPane.showMessageDialog(this, "Wastage data saved successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);

            } catch (SQLException ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error saving wastage data: " + ex.getMessage(), "DB Error", JOptionPane.ERROR_MESSAGE);
            }

        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Please enter valid numbers for wastage.", "Input Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}

