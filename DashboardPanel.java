import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

/**
 * The main student dashboard.
 * Now fully functional, writing data to the database, and showing the food wastage banner.
 */
public class DashboardPanel extends JPanel {

    private Hostel hostel;
    private Student loggedInStudent;

    // --- Banner for Food Wastage ---
    private JLabel bannerLabel;

    // UI Components for Mess Panel
    private JCheckBox messCheckBox;
    private JButton messSubmitButton;

    // UI Components for Cleaning Panel
    private JCheckBox cleaningCheckBox;
    private JButton cleaningSubmitButton;

    // UI Components for Leave Panel
    private JTextField leaveStartDateField;
    private JTextField leaveEndDateField;
    private JTextArea leaveReasonArea;
    private JButton leaveSubmitButton;
    private DefaultTableModel leaveHistoryModel;

    // UI Components for Outpass Panel
    private JTextField outpassDateField;
    private JTextField timeOutField;
    private JTextField timeInField;
    private JTextArea outpassReasonArea;
    private JButton outpassSubmitButton;
    private DefaultTableModel outpassHistoryModel;


    public DashboardPanel(Hostel hostel) {
        this.hostel = hostel;
        this.loggedInStudent = hostel.getLoggedInStudent();

        if (this.loggedInStudent == null) {
            // This should not happen if MainFrame logic is correct
            setLayout(new BorderLayout());
            add(new JLabel("Error: No student logged in."), BorderLayout.CENTER);
            return;
        }

        setLayout(new BorderLayout(15, 15));
        setBorder(new EmptyBorder(15, 15, 15, 15));
        setBackground(new Color(245, 247, 250)); // Light background

        // --- Add Food Wastage Banner to the top ---
        bannerLabel = createBannerLabel();
        add(bannerLabel, BorderLayout.NORTH);

        // 2x2 Grid for the main content
        JPanel quadrantPanel = new JPanel(new GridLayout(2, 2, 15, 15));
        quadrantPanel.setOpaque(false);

        quadrantPanel.add(createMessPanel());
        quadrantPanel.add(createCleaningStatusPanel());
        quadrantPanel.add(createLeaveApplicationPanel());

        // Replaced Future Availability with Outpass
        quadrantPanel.add(createOutpassPanel());

        add(quadrantPanel, BorderLayout.CENTER);

        // Add history panel at the bottom
        add(createHistoryPanel(), BorderLayout.SOUTH);

        // Initial check to see if student has already submitted today
        refreshDashboard();
    }

    /**
     * Refreshes the state of buttons and banner based on DB
     */
    public void refreshDashboard() {
        updateBanner(); // Load and display food wastage
        checkMessSubmissionStatus();
        checkCleaningSubmissionStatus();
        loadLeaveHistory();
        loadOutpassHistory();
    }

    // --- Banner Methods ---

    /**
     * Creates the banner label, initially empty.
     */
    private JLabel createBannerLabel() {
        JLabel label = new JLabel("Loading food wastage data...", SwingConstants.CENTER);
        label.setOpaque(true);
        label.setFont(new Font("Segoe UI", Font.BOLD, 14));
        label.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        return label;
    }

    /**
     * Fetches wastage data from DB and updates the banner.
     */
    private void updateBanner() {
        double thisMonth = 0.0;
        double lastMonth = 0.0;

        String sql = "SELECT metric_key, metric_value FROM hostel_metrics WHERE metric_key IN ('this_month_wastage', 'last_month_wastage')";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                if (rs.getString("metric_key").equals("this_month_wastage")) {
                    thisMonth = rs.getDouble("metric_value");
                } else if (rs.getString("metric_key").equals("last_month_wastage")) {
                    lastMonth = rs.getDouble("metric_value");
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            bannerLabel.setText("Could not load food wastage data.");
            bannerLabel.setBackground(new Color(240, 240, 240));
            bannerLabel.setForeground(Color.RED);
            return;
        }

        // Now, format the banner text
        String trendText;
        Color trendColor;
        Color bgColor;

        double percentChange = 0.0;
        if (lastMonth > 0) {
            percentChange = ((thisMonth - lastMonth) / lastMonth) * 100.0;
        } else if (thisMonth > 0) {
            percentChange = 100.0; // From 0 to >0 is 100% increase
        }

        if (percentChange > 0) {
            trendText = String.format("↑ %.1f%% more than last month. Let's improve!", percentChange);
            trendColor = new Color(192, 57, 43); // Red
            bgColor = new Color(255, 240, 240);
        } else if (percentChange < 0) {
            trendText = String.format("↓ %.1f%% less than last month. Let's aim lower!", Math.abs(percentChange));
            trendColor = new Color(39, 174, 96); // Green
            bgColor = new Color(235, 255, 240);
        } else {
            trendText = "Same as last month.";
            trendColor = new Color(52, 73, 94); // Dark blue/grey
            bgColor = new Color(245, 245, 245);
        }

        bannerLabel.setText(String.format("This month's food wastage: %.1f kg — %s", thisMonth, trendText));
        bannerLabel.setForeground(trendColor);
        bannerLabel.setBackground(bgColor);
    }


    // --- Mess Panel ---

    private JPanel createMessPanel() {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);

        messCheckBox = new JCheckBox("I will eat Dinner in the mess today");
        messCheckBox.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        messCheckBox.setAlignmentX(Component.CENTER_ALIGNMENT);
        messCheckBox.setOpaque(false);

        messSubmitButton = new JButton("Submit");
        messSubmitButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        styleButton(messSubmitButton, new Color(52, 152, 219), Color.WHITE, 14, true);

        content.add(Box.createVerticalGlue());
        content.add(messCheckBox);
        content.add(Box.createVerticalStrut(10));
        content.add(messSubmitButton);
        content.add(Box.createVerticalGlue());

        // Add action listener
        messSubmitButton.addActionListener(e -> submitMessAttendance());

        return createCardPanel("Today's Dinner", "🍽️", content);
    }

    private void submitMessAttendance() {
        boolean isAttending = messCheckBox.isSelected();
        String sql = "INSERT INTO mess_attendance (student_roll, attendance_date, is_attending) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE is_attending = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, loggedInStudent.getRollNumber());
            pstmt.setDate(2, java.sql.Date.valueOf(LocalDate.now()));
            pstmt.setBoolean(3, isAttending);
            pstmt.setBoolean(4, isAttending); // For the ON DUPLICATE part

            pstmt.executeUpdate();

            JOptionPane.showMessageDialog(this, "Mess attendance submitted successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
            checkMessSubmissionStatus(); // Disable button after submission

        } catch (SQLException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Database error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void checkMessSubmissionStatus() {
        String sql = "SELECT is_attending FROM mess_attendance WHERE student_roll = ? AND attendance_date = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, loggedInStudent.getRollNumber());
            pstmt.setDate(2, java.sql.Date.valueOf(LocalDate.now()));

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    // Already submitted
                    boolean isAttending = rs.getBoolean("is_attending");
                    messCheckBox.setSelected(isAttending);
                    messSubmitButton.setText("Submitted Today");
                    messSubmitButton.setEnabled(false);
                } else {
                    // Not submitted yet
                    messSubmitButton.setText("Submit");
                    messSubmitButton.setEnabled(true);
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    // --- Cleaning Panel ---

    private JPanel createCleaningStatusPanel() {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);

        cleaningCheckBox = new JCheckBox("My room has been cleaned today");
        cleaningCheckBox.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        cleaningCheckBox.setAlignmentX(Component.CENTER_ALIGNMENT);
        cleaningCheckBox.setOpaque(false);

        cleaningSubmitButton = new JButton("Report Status");
        cleaningSubmitButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        styleButton(cleaningSubmitButton, new Color(46, 204, 113), Color.WHITE, 14, true);

        content.add(Box.createVerticalGlue());
        content.add(cleaningCheckBox);
        content.add(Box.createVerticalStrut(10));
        content.add(cleaningSubmitButton);
        content.add(Box.createVerticalGlue());

        cleaningSubmitButton.addActionListener(e -> submitCleaningReport());

        return createCardPanel("Daily Cleaning Status", "🧹", content);
    }

    private void submitCleaningReport() {
        boolean isCleaned = cleaningCheckBox.isSelected();
        String sql = "INSERT INTO cleaning_reports (student_roll, report_date, is_cleaned) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE is_cleaned = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, loggedInStudent.getRollNumber());
            pstmt.setDate(2, java.sql.Date.valueOf(LocalDate.now()));
            pstmt.setBoolean(3, isCleaned);
            pstmt.setBoolean(4, isCleaned); // For the ON DUPLICATE part

            pstmt.executeUpdate();

            JOptionPane.showMessageDialog(this, "Cleaning report submitted successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
            checkCleaningSubmissionStatus(); // Disable button after submission

        } catch (SQLException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Database error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void checkCleaningSubmissionStatus() {
        String sql = "SELECT is_cleaned FROM cleaning_reports WHERE student_roll = ? AND report_date = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, loggedInStudent.getRollNumber());
            pstmt.setDate(2, java.sql.Date.valueOf(LocalDate.now()));

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    // Already submitted
                    cleaningCheckBox.setSelected(rs.getBoolean("is_cleaned"));
                    cleaningSubmitButton.setText("Reported Today");
                    cleaningSubmitButton.setEnabled(false);
                } else {
                    // Not submitted yet
                    cleaningSubmitButton.setText("Report Status");
                    cleaningSubmitButton.setEnabled(true);
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    // --- Leave Application Panel ---

    private JPanel createLeaveApplicationPanel() {
        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setOpaque(false);

        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setOpaque(false);
        formPanel.setBorder(new EmptyBorder(5, 5, 5, 5)); // Add padding
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Start Date
        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.EAST;
        formPanel.add(new JLabel("Start Date (YYYY-MM-DD):"), gbc);
        gbc.gridx = 1; gbc.gridy = 0; gbc.anchor = GridBagConstraints.WEST;
        leaveStartDateField = new JTextField(20);
        formPanel.add(leaveStartDateField, gbc);

        // End Date
        gbc.gridx = 0; gbc.gridy = 1; gbc.anchor = GridBagConstraints.EAST;
        formPanel.add(new JLabel("End Date (YYYY-MM-DD):"), gbc);
        gbc.gridx = 1; gbc.gridy = 1; gbc.anchor = GridBagConstraints.WEST;
        leaveEndDateField = new JTextField(20);
        formPanel.add(leaveEndDateField, gbc);

        // Reason
        gbc.gridx = 0; gbc.gridy = 2; gbc.anchor = GridBagConstraints.NORTHEAST;
        formPanel.add(new JLabel("Reason:"), gbc);
        gbc.gridx = 1; gbc.gridy = 2; gbc.fill = GridBagConstraints.BOTH; gbc.weightx = 1.0; gbc.weighty = 1.0;
        leaveReasonArea = new JTextArea(4, 20);
        leaveReasonArea.setLineWrap(true);
        leaveReasonArea.setWrapStyleWord(true);
        JScrollPane reasonScrollPane = new JScrollPane(leaveReasonArea);
        formPanel.add(reasonScrollPane, gbc);

        // Wrap form in a JScrollPane to prevent hiding fields
        JScrollPane formScrollPane = new JScrollPane(formPanel);
        formScrollPane.setBorder(BorderFactory.createEmptyBorder());
        content.add(formScrollPane, BorderLayout.CENTER);

        leaveSubmitButton = new JButton("Submit Leave Request");
        styleButton(leaveSubmitButton, new Color(155, 89, 182), Color.WHITE, 14, true); // Purple
        leaveSubmitButton.addActionListener(e -> submitLeaveRequest());
        content.add(leaveSubmitButton, BorderLayout.SOUTH);

        return createCardPanel("Apply for Leave", "📝", content);
    }

    private void submitLeaveRequest() {
        String startDateStr = leaveStartDateField.getText().trim();
        String endDateStr = leaveEndDateField.getText().trim();
        String reason = leaveReasonArea.getText().trim();

        if (startDateStr.isEmpty() || endDateStr.isEmpty() || reason.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please fill in all leave fields.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            // Validate dates
            LocalDate startDate = LocalDate.parse(startDateStr);
            LocalDate endDate = LocalDate.parse(endDateStr);

            if (endDate.isBefore(startDate)) {
                JOptionPane.showMessageDialog(this, "End date cannot be before start date.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            String sql = "INSERT INTO leave_requests (student_roll, start_date, end_date, reason, status) VALUES (?, ?, ?, ?, ?)";

            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setString(1, loggedInStudent.getRollNumber());
                pstmt.setDate(2, java.sql.Date.valueOf(startDate));
                pstmt.setDate(3, java.sql.Date.valueOf(endDate));
                pstmt.setString(4, reason);
                pstmt.setString(5, "Pending");

                pstmt.executeUpdate();

                JOptionPane.showMessageDialog(this, "Leave request submitted successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);

                // Clear fields and refresh history
                leaveStartDateField.setText("");
                leaveEndDateField.setText("");
                leaveReasonArea.setText("");
                loadLeaveHistory();

            } catch (SQLException ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Database error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }

        } catch (java.time.format.DateTimeParseException ex) {
            JOptionPane.showMessageDialog(this, "Invalid date format. Please use YYYY-MM-DD.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // --- Outpass Panel ---

    private JPanel createOutpassPanel() {
        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setOpaque(false);

        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setOpaque(false);
        formPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Date
        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.EAST;
        formPanel.add(new JLabel("Date (YYYY-MM-DD):"), gbc);
        gbc.gridx = 1; gbc.gridy = 0; gbc.anchor = GridBagConstraints.WEST;
        outpassDateField = new JTextField(20);
        formPanel.add(outpassDateField, gbc);

        // Time Out
        gbc.gridx = 0; gbc.gridy = 1; gbc.anchor = GridBagConstraints.EAST;
        formPanel.add(new JLabel("Time Out (HH:MM):"), gbc);
        gbc.gridx = 1; gbc.gridy = 1; gbc.anchor = GridBagConstraints.WEST;
        timeOutField = new JTextField(20);
        formPanel.add(timeOutField, gbc);

        // Time In
        gbc.gridx = 0; gbc.gridy = 2; gbc.anchor = GridBagConstraints.EAST;
        formPanel.add(new JLabel("Time In (HH:MM):"), gbc);
        gbc.gridx = 1; gbc.gridy = 2; gbc.anchor = GridBagConstraints.WEST;
        timeInField = new JTextField(20);
        formPanel.add(timeInField, gbc);

        // Reason
        gbc.gridx = 0; gbc.gridy = 3; gbc.anchor = GridBagConstraints.NORTHEAST;
        formPanel.add(new JLabel("Reason:"), gbc);
        gbc.gridx = 1; gbc.gridy = 3; gbc.fill = GridBagConstraints.BOTH; gbc.weightx = 1.0; gbc.weighty = 1.0;
        outpassReasonArea = new JTextArea(4, 20);
        outpassReasonArea.setLineWrap(true);
        outpassReasonArea.setWrapStyleWord(true);
        JScrollPane reasonScrollPane = new JScrollPane(outpassReasonArea);
        formPanel.add(reasonScrollPane, gbc);

        // Wrap form in a JScrollPane to prevent hiding fields
        JScrollPane formScrollPane = new JScrollPane(formPanel);
        formScrollPane.setBorder(BorderFactory.createEmptyBorder());
        content.add(formScrollPane, BorderLayout.CENTER);

        outpassSubmitButton = new JButton("Submit Outpass Request");
        styleButton(outpassSubmitButton, new Color(230, 126, 34), Color.WHITE, 14, true); // Orange
        outpassSubmitButton.addActionListener(e -> submitOutpassRequest());
        content.add(outpassSubmitButton, BorderLayout.SOUTH);

        return createCardPanel("Apply for Outpass", "🚶", content);
    }

    private void submitOutpassRequest() {
        String dateStr = outpassDateField.getText().trim();
        String timeOut = timeOutField.getText().trim();
        String timeIn = timeInField.getText().trim();
        String reason = outpassReasonArea.getText().trim();

        if (dateStr.isEmpty() || timeOut.isEmpty() || timeIn.isEmpty() || reason.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please fill in all outpass fields.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            // Validate date
            LocalDate outpassDate = LocalDate.parse(dateStr);

            String sql = "INSERT INTO outpass_requests (student_roll, outpass_date, time_out, time_in, reason, status) VALUES (?, ?, ?, ?, ?, ?)";

            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setString(1, loggedInStudent.getRollNumber());
                pstmt.setDate(2, java.sql.Date.valueOf(outpassDate));
                pstmt.setString(3, timeOut);
                pstmt.setString(4, timeIn);
                pstmt.setString(5, reason);
                pstmt.setString(6, "Pending");

                pstmt.executeUpdate();

                JOptionPane.showMessageDialog(this, "Outpass request submitted successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);

                // Clear fields and refresh history
                outpassDateField.setText("");
                timeOutField.setText("");
                timeInField.setText("");
                outpassReasonArea.setText("");
                loadOutpassHistory();

            } catch (SQLException ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Database error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }

        } catch (java.time.format.DateTimeParseException ex) {
            JOptionPane.showMessageDialog(this, "Invalid date format. Please use YYYY-MM-DD.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }


    // --- History Panel ---

    private JPanel createHistoryPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new EmptyBorder(15, 0, 0, 0)); // Add some space above

        JTabbedPane historyTabs = new JTabbedPane();
        historyTabs.setFont(new Font("Segoe UI", Font.BOLD, 12));

        // --- Leave History Tab ---
        JPanel leaveHistoryPanel = new JPanel(new BorderLayout());
        String[] leaveColumnNames = {"Start Date", "End Date", "Reason", "Status"};
        leaveHistoryModel = new DefaultTableModel(leaveColumnNames, 0);
        JTable leaveTable = new JTable(leaveHistoryModel);
        leaveHistoryPanel.add(new JScrollPane(leaveTable), BorderLayout.CENTER);

        // --- Outpass History Tab ---
        JPanel outpassHistoryPanel = new JPanel(new BorderLayout());
        String[] outpassColumnNames = {"Date", "Time Out", "Time In", "Reason", "Status"};
        outpassHistoryModel = new DefaultTableModel(outpassColumnNames, 0);
        JTable outpassTable = new JTable(outpassHistoryModel);
        outpassHistoryPanel.add(new JScrollPane(outpassTable), BorderLayout.CENTER);

        historyTabs.addTab("My Leave History", leaveHistoryPanel);
        historyTabs.addTab("My Outpass History", outpassHistoryPanel);

        panel.add(historyTabs, BorderLayout.CENTER);

        // Reduced height
        panel.setPreferredSize(new Dimension(800, 180));

        return panel;
    }

    private void loadLeaveHistory() {
        if (leaveHistoryModel == null) return; // Panel might not be initialized yet
        leaveHistoryModel.setRowCount(0);
        String sql = "SELECT start_date, end_date, reason, status FROM leave_requests WHERE student_roll = ? ORDER BY start_date DESC";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, loggedInStudent.getRollNumber());
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    leaveHistoryModel.addRow(new Object[]{
                            rs.getDate("start_date").toLocalDate(),
                            rs.getDate("end_date").toLocalDate(),
                            rs.getString("reason"),
                            rs.getString("status")
                    });
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    private void loadOutpassHistory() {
        if (outpassHistoryModel == null) return; // Panel might not be initialized yet
        outpassHistoryModel.setRowCount(0);
        String sql = "SELECT outpass_date, time_out, time_in, reason, status FROM outpass_requests WHERE student_roll = ? ORDER BY outpass_date DESC";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, loggedInStudent.getRollNumber());
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    outpassHistoryModel.addRow(new Object[]{
                            rs.getDate("outpass_date").toLocalDate(),
                            rs.getString("time_out"),
                            rs.getString("time_in"),
                            rs.getString("reason"),
                            rs.getString("status")
                    });
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    // --- Utility Methods ---

    private JPanel createCardPanel(String title, String iconText, JPanel contentPanel) {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(210, 210, 210), 1),
                new EmptyBorder(10, 10, 10, 10)));

        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
        headerPanel.setOpaque(false);

        JLabel iconLabel = new JLabel(iconText, SwingConstants.CENTER);
        iconLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 24));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        titleLabel.setForeground(new Color(44, 62, 80));

        headerPanel.add(iconLabel);
        headerPanel.add(titleLabel);

        panel.add(headerPanel, BorderLayout.NORTH);
        panel.add(contentPanel, BorderLayout.CENTER);

        return panel;
    }

    private void styleButton(JButton button, Color bgColor, Color fgColor, int fontSize, boolean enableHover) {
        button.setBackground(bgColor);
        button.setForeground(fgColor);
        button.setFocusPainted(false);
        button.setFont(new Font("Segoe UI", Font.BOLD, fontSize));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        if (enableHover) {
            button.addMouseListener(new java.awt.event.MouseAdapter() {
                private final Color originalColor = bgColor;
                public void mouseEntered(java.awt.event.MouseEvent evt) {
                    button.setBackground(originalColor.darker());
                }
                public void mouseExited(java.awt.event.MouseEvent evt) {
                    button.setBackground(originalColor);
                }
            });
        }
    }
}

