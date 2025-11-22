package billgenerator;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

// ---------------- MAIN ----------------
public class DynamicGUI {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LoginScreen());
    }
}

// ---------------- NOTIFICATION CENTER (persistence for admin/customer) ----------------
class NotificationCenter {
    private static final File adminFile = new File("admin_notifications.txt");
    private static final File customerFile = new File("customer_notifications.txt");

    // add admin-targeted notification
    public static synchronized void addAdminNotification(String msg) {
        addToFile(adminFile, format(msg));
    }

    // add customer-targeted notification
    public static synchronized void addCustomerNotification(String msg) {
        addToFile(customerFile, format(msg));
    }

    public static synchronized List<String> getAdminNotifications() {
        return readFile(adminFile);
    }

    public static synchronized List<String> getCustomerNotifications() {
        return readFile(customerFile);
    }

    private static String format(String msg) {
        return "[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "] " + msg;
    }

    private static void addToFile(File f, String line) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(f, true))) {
            bw.write(line);
            bw.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static List<String> readFile(File f) {
        List<String> out = new ArrayList<>();
        if (!f.exists()) return out;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) out.add(line);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return out;
    }
}

// small reusable dialog to show list of strings
class NotificationsDialog extends JDialog {
    public NotificationsDialog(JFrame parent, String title, List<String> messages) {
        super(parent, title, true);
        setSize(480, 360);
        setLocationRelativeTo(parent);
        DefaultListModel<String> model = new DefaultListModel<>();
        for (String m : messages) model.addElement(m);
        JList<String> list = new JList<>(model);
        JScrollPane sp = new JScrollPane(list);
        add(sp, BorderLayout.CENTER);
        JButton close = new JButton("Close");
        close.addActionListener(e -> dispose());
        add(close, BorderLayout.SOUTH);
        setVisible(true);
    }
}

// ---------------- LOGIN SCREEN ----------------
class LoginScreen extends JFrame {
    JTextField usernameField;
    JPasswordField passwordField;
    JButton loginButton, registerButton;
    JLabel statusLabel;
    JComboBox<String> roleBox;
    JCheckBox showPasswordBox;

    static Map<String, String> customers = new HashMap<>();
    static File customerFile = new File("customers.txt");

    // profiles: username -> [first, middle, last]
    static Map<String, String[]> profiles = new HashMap<>();
    static File profilesFile = new File("profiles.txt");

    public LoginScreen() {
        setTitle("Dynamic POS Login");
        setSize(400, 300);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        loadCustomers(); // keep your original behavior
        loadProfiles();  // load name parts if any

        JPanel panel = new JPanel(new GridLayout(6, 2, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        panel.add(new JLabel("Role:"));
        roleBox = new JComboBox<>(new String[]{"Admin", "Customer"});
        panel.add(roleBox);

        panel.add(new JLabel("Username:"));
        usernameField = new JTextField();
        panel.add(usernameField);

        panel.add(new JLabel("Password:"));
        passwordField = new JPasswordField();
        panel.add(passwordField);

        // Show password toggle
        showPasswordBox = new JCheckBox("Show Password");
        showPasswordBox.addActionListener(e -> {
            if (showPasswordBox.isSelected()) {
                passwordField.setEchoChar((char) 0);
            } else {
                passwordField.setEchoChar('•');
            }
        });
        panel.add(showPasswordBox);
        panel.add(new JLabel("")); // filler

        loginButton = new JButton("Login");
        registerButton = new JButton("Create Account");
        panel.add(loginButton);
        panel.add(registerButton);

        statusLabel = new JLabel("");
        panel.add(statusLabel);

        loginButton.addActionListener(e -> authenticate());
        registerButton.addActionListener(e -> {
            dispose();
            new RegisterScreen(this);
        });

        add(panel);
        setVisible(true);
    }

    // Load saved customer accounts from file
    private void loadCustomers() {
        customers.clear();
        if (customerFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(customerFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(":");
                    if (parts.length == 2) {
                        customers.put(parts[0], parts[1]);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // Load profiles: username:first:middle:last
    private void loadProfiles() {
        profiles.clear();
        if (profilesFile.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(profilesFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] p = line.split(":", 4);
                    if (p.length == 4) {
                        profiles.put(p[0], new String[]{p[1], p[2], p[3]});
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // Save new customer to file
    public static void saveCustomer(String username, String password) {
        customers.put(username, password);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(customerFile, true))) {
            writer.write(username + ":" + password);
            writer.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Save profile (create or update single profile entry)
    public static void saveProfile(String username, String first, String middle, String last) {
        profiles.put(username, new String[]{first == null ? "" : first, middle == null ? "" : middle, last == null ? "" : last});
        // rewrite profiles file
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(profilesFile))) {
            for (Map.Entry<String, String[]> e : profiles.entrySet()) {
                String[] v = e.getValue();
                bw.write(e.getKey() + ":" + v[0] + ":" + v[1] + ":" + v[2]);
                bw.newLine();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    // Login authentication
    private void authenticate() {
        String role = (String) roleBox.getSelectedItem();
        String username = usernameField.getText();
        String password = String.valueOf(passwordField.getPassword());

        if ("Admin".equals(role)) {
            if (username.equals("admin") && password.equals("admin123")) {
                JOptionPane.showMessageDialog(this, "Welcome Admin!");
                dispose();
                new AdminDashboard();
            } else {
                statusLabel.setText("Invalid Admin credentials");
            }
        } else { // Customer
            if (customers.containsKey(username) && customers.get(username).equals(password)) {
                JOptionPane.showMessageDialog(this, "Welcome " + username + "!");
                dispose();
                new CashierPanel(username);
            } else {
                statusLabel.setText("Invalid Customer credentials");
            }
        }
    }
}

// ---------------- REGISTER SCREEN ----------------
class RegisterScreen extends JFrame {
    JTextField usernameField;
    JPasswordField passwordField, confirmPasswordField;
    JCheckBox showPasswordBox;
    JButton registerButton, backButton;

    public RegisterScreen(JFrame parent) {
        setTitle("Create Customer Account");
        setSize(400, 330);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        JPanel panel = new JPanel(new GridLayout(8, 2, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        panel.add(new JLabel("Choose Username:"));
        usernameField = new JTextField();
        panel.add(usernameField);

        panel.add(new JLabel("Choose Password:"));
        passwordField = new JPasswordField();
        panel.add(passwordField);

        panel.add(new JLabel("Confirm Password:"));
        confirmPasswordField = new JPasswordField();
        panel.add(confirmPasswordField);

        // Basic profile fields for first/middle/last name
        panel.add(new JLabel("First Name (optional):"));
        JTextField firstField = new JTextField();
        panel.add(firstField);

        panel.add(new JLabel("Middle Name (optional):"));
        JTextField middleField = new JTextField();
        panel.add(middleField);

        panel.add(new JLabel("Last Name (optional):"));
        JTextField lastField = new JTextField();
        panel.add(lastField);

        // Show password checkbox
        showPasswordBox = new JCheckBox("Show Passwords");
        showPasswordBox.addActionListener(e -> {
            if (showPasswordBox.isSelected()) {
                passwordField.setEchoChar((char) 0);
                confirmPasswordField.setEchoChar((char) 0);
            } else {
                passwordField.setEchoChar('•');
                confirmPasswordField.setEchoChar('•');
            }
        });
        panel.add(showPasswordBox);
        panel.add(new JLabel("")); // filler

        registerButton = new JButton("Register");
        backButton = new JButton("Back to Login");
        panel.add(registerButton);
        panel.add(backButton);

        add(panel);

        registerButton.addActionListener(e -> {
            String username = usernameField.getText().trim();
            String password = String.valueOf(passwordField.getPassword());
            String confirmPassword = String.valueOf(confirmPasswordField.getPassword());
            String first = firstField.getText().trim();
            String middle = middleField.getText().trim();
            String last = lastField.getText().trim();

            if (username.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Username and passwords are required!");
                return;
            }
            if (!password.equals(confirmPassword)) {
                JOptionPane.showMessageDialog(this, "Passwords do not match!");
                return;
            }
            if (LoginScreen.customers.containsKey(username)) {
                JOptionPane.showMessageDialog(this, "Username already exists!");
                return;
            }

            LoginScreen.saveCustomer(username, password);
            // Save profile even if empty fields
            LoginScreen.saveProfile(username, first, middle, last);

            JOptionPane.showMessageDialog(this, "Account created successfully!");
            dispose();
            parent.setVisible(true);
        });

        backButton.addActionListener(e -> {
            dispose();
            parent.setVisible(true);
        });

        setVisible(true);
    }
}

// ---------------- ADMIN DASHBOARD ----------------
class AdminDashboard extends JFrame {
    private JTable menuTable, salesTable, transactionTable;
    private DefaultTableModel menuModel, salesModel, transactionModel;
    private File menuFile = new File("menu.txt");
    private File salesFile = new File("sales.txt");
    private File transactionsFile = new File("transactions.txt");

    // structured plain-text CSV files used by Analytics
    private final File ordersFile = new File("orders.csv");         // datetime,customer,total
    private final File orderItemsFile = new File("order_items.csv");// datetime,item,qty,line_total

    public AdminDashboard() {
        setTitle("Admin Dashboard");
        setSize(1000, 650);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JTabbedPane tabs = new JTabbedPane();

        // MENU MANAGEMENT
        menuModel = new DefaultTableModel(new String[]{"Item", "Price", "Category"}, 0);
        menuTable = new JTable(menuModel);
        loadMenu();

        JButton addBtn = new JButton("Add Item");
        JButton editBtn = new JButton("Edit Item");
        JButton delBtn = new JButton("Delete Item");
        JButton ordersBtn = new JButton("Orders (Mark Received)");
        JButton notifBtn = new JButton("🔔 Notifications");

        addBtn.addActionListener(e -> {
            addMenuItem();
            // notify customers that menu changed
            NotificationCenter.addCustomerNotification("Admin added new menu item.");
        });
        editBtn.addActionListener(e -> {
            editMenuItem();
            NotificationCenter.addCustomerNotification("Admin edited a menu item.");
        });
        delBtn.addActionListener(e -> {
            deleteMenuItem();
            NotificationCenter.addCustomerNotification("Admin deleted a menu item.");
        });

        // Open Orders dialog where Admin can mark orders as received (this will notify the corresponding customer)
        ordersBtn.addActionListener(e -> showOrdersDialog());

        // Admin notification viewer
        notifBtn.addActionListener(e -> {
            List<String> notifs = NotificationCenter.getAdminNotifications();
            new NotificationsDialog(this, "Admin Notifications", notifs);
        });

        JPanel menuPanel = new JPanel(new BorderLayout());
        menuPanel.add(new JScrollPane(menuTable), BorderLayout.CENTER);

        JPanel menuButtons = new JPanel();
        menuButtons.add(addBtn);
        menuButtons.add(editBtn);
        menuButtons.add(delBtn);
        menuButtons.add(ordersBtn);
        menuButtons.add(notifBtn);
        menuPanel.add(menuButtons, BorderLayout.SOUTH);

        tabs.add("Menu Management", menuPanel);

        // SALES DASHBOARD (legacy table)
        salesModel = new DefaultTableModel(new String[]{"Customer", "Total"}, 0);
        salesTable = new JTable(salesModel);
        loadSales();
        tabs.add("Sales Dashboard", new JScrollPane(salesTable));

        // TRANSACTIONS (legacy receipts)
        transactionModel = new DefaultTableModel(new String[]{"Receipt"}, 0);
        transactionTable = new JTable(transactionModel);
        loadTransactions();
        tabs.add("Transactions", new JScrollPane(transactionTable));

        // Analytics (Top Sellers + Sales Graphs)
        tabs.add("Analytics", new AnalyticsPanel(ordersFile, orderItemsFile));

        add(tabs);
        setVisible(true);
    }

    private void showOrdersDialog() {
        // Build list of orders from orders.csv (datetime,customer,total)
        List<String[]> orders = new ArrayList<>();
        if (ordersFile.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(ordersFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] p = parseCsvLine(line);
                    if (p.length >= 3) {
                        orders.add(new String[]{p[0], unescape(p[1]), p[2]});
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (orders.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No orders found.");
            return;
        }

        DefaultListModel<String> model = new DefaultListModel<>();
        for (int i = 0; i < orders.size(); i++) {
            String[] o = orders.get(i);
            model.addElement(i + ": " + o[0] + " | " + o[1] + " | ₱" + o[2]);
        }
        JList<String> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane sp = new JScrollPane(list);

        JButton markBtn = new JButton("Mark Selected Order as Received (notify customer)");
        markBtn.addActionListener(e -> {
            int sel = list.getSelectedIndex();
            if (sel == -1) {
                JOptionPane.showMessageDialog(this, "Select an order first.");
                return;
            }
            String[] o = orders.get(sel);
            String datetime = o[0];
            String customer = o[1];
            String total = o[2];

            // Notify customer that admin got/received their order
            NotificationCenter.addCustomerNotification("Admin received order for " + customer + " (₱" + total + ") at " + datetime);
            // Also add admin log
            NotificationCenter.addAdminNotification("Marked order as received for " + customer + " (₱" + total + ") at " + datetime);

            JOptionPane.showMessageDialog(this, "Customer notified that their order was received.");
        });

        JPanel pnl = new JPanel(new BorderLayout());
        pnl.add(sp, BorderLayout.CENTER);
        pnl.add(markBtn, BorderLayout.SOUTH);

        JDialog dlg = new JDialog(this, "Orders - Mark Received", true);
        dlg.setSize(700, 400);
        dlg.setLocationRelativeTo(this);
        dlg.add(pnl);
        dlg.setVisible(true);
    }

    private void loadMenu() {
        menuModel.setRowCount(0);
        if (menuFile.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(menuFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] parts = line.split(",");
                    if (parts.length == 3) {
                        menuModel.addRow(parts);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void saveMenu() {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(menuFile))) {
            for (int i = 0; i < menuModel.getRowCount(); i++) {
                bw.write(menuModel.getValueAt(i, 0) + "," + menuModel.getValueAt(i, 1) + "," + menuModel.getValueAt(i, 2));
                bw.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void addMenuItem() {
        String item = JOptionPane.showInputDialog(this, "Enter item name:");
        String price = JOptionPane.showInputDialog(this, "Enter price:");
        String[] categories = {"Food", "Drinks", "Desserts", "Combo Meal", "Snacks"};
        String category = (String) JOptionPane.showInputDialog(this, "Select Category:", "Category", JOptionPane.PLAIN_MESSAGE, null, categories, categories[0]);

        if (item != null && price != null && category != null && !item.isEmpty() && !price.isEmpty()) {
            menuModel.addRow(new String[]{item, price, category});
            saveMenu();
            // notify customers (specific message with item)
            NotificationCenter.addCustomerNotification("Admin added new item: " + item);
        }
    }

    private void editMenuItem() {
        int row = menuTable.getSelectedRow();
        if (row != -1) {
            String newItem = JOptionPane.showInputDialog(this, "Edit item name:", menuModel.getValueAt(row, 0));
            String newPrice = JOptionPane.showInputDialog(this, "Edit price:", menuModel.getValueAt(row, 1));
            String newCategory = JOptionPane.showInputDialog(this, "Edit category:", menuModel.getValueAt(row, 2));

            String oldItem = (String) menuModel.getValueAt(row, 0);

            if (newItem != null) menuModel.setValueAt(newItem, row, 0);
            if (newPrice != null) menuModel.setValueAt(newPrice, row, 1);
            if (newCategory != null) menuModel.setValueAt(newCategory, row, 2);
            saveMenu();
            NotificationCenter.addCustomerNotification("Admin edited item: " + oldItem + " -> " + (newItem == null ? oldItem : newItem));
        }
    }

    private void deleteMenuItem() {
        int row = menuTable.getSelectedRow();
        if (row != -1) {
            String item = (String) menuModel.getValueAt(row, 0);
            menuModel.removeRow(row);
            saveMenu();
            NotificationCenter.addCustomerNotification("Admin deleted item: " + item);
        }
    }

    private void loadSales() {
        salesModel.setRowCount(0);
        if (salesFile.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(salesFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] parts = line.split(",");
                    if (parts.length == 2) {
                        salesModel.addRow(parts);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void loadTransactions() {
        transactionModel.setRowCount(0);
        if (transactionsFile.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(transactionsFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    transactionModel.addRow(new String[]{line});
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // --- reuse CSV helpers from AnalyticsPanel below ---
    private String[] parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '\"') {
                    sb.append('\"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                out.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        out.add(sb.toString());
        return out.toArray(new String[0]);
    }

    private String unescape(String s) {
        if (s.startsWith("\"") && s.endsWith("\"")) {
            String inner = s.substring(1, s.length() - 1);
            return inner.replace("\"\"", "\"");
        }
        return s;
    }
}

// ---------------- CASHIER PANEL ----------------
class CashierPanel extends JFrame {
    private String customerName;
    private DefaultTableModel bagModel;
    private File menuFile = new File("menu.txt");
    private File salesFile = new File("sales.txt");
    private File transactionsFile = new File("transactions.txt");

    // structured plain-text files for analytics
    private final File ordersFile = new File("orders.csv");          // datetime,customer,total
    private final File orderItemsFile = new File("order_items.csv"); // datetime,item,qty,line_total

    public CashierPanel(String customerName) {
        this.customerName = customerName;

        setTitle("Customer Menu - " + customerName);
        setSize(800, 600);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JTabbedPane categoryTabs = new JTabbedPane();

        // 5 categories
        String[] categories = {"Food", "Drinks", "Desserts", "Combo Meal", "Snacks"};
        for (String category : categories) {
            categoryTabs.add(category, createCategoryPanel(category));
        }

        // Bag (selected items)
        bagModel = new DefaultTableModel(new String[]{"Item", "Price", "Qty"}, 0);
        JTable bagTable = new JTable(bagModel);

        JButton plusBtn = new JButton("+");
        JButton minusBtn = new JButton("-");
        JButton finishBtn = new JButton("Finish Order");
        JButton notifBtn = new JButton("🔔 Notifications");

        // Increment quantity
        plusBtn.addActionListener(e -> {
            int row = bagTable.getSelectedRow();
            if (row != -1) {
                int qty = (int) bagModel.getValueAt(row, 2);
                bagModel.setValueAt(qty + 1, row, 2);
            }
        });

        // Decrement quantity
        minusBtn.addActionListener(e -> {
            int row = bagTable.getSelectedRow();
            if (row != -1) {
                int qty = (int) bagModel.getValueAt(row, 2);
                if (qty > 1) {
                    bagModel.setValueAt(qty - 1, row, 2);
                } else {
                    bagModel.removeRow(row); // remove if qty goes to 0
                }
            }
        });

        // Finish order
        finishBtn.addActionListener(e -> finishOrder());

        // Notifications button for customer - shows notifications intended for customers
        notifBtn.addActionListener(e -> {
            List<String> notifs = NotificationCenter.getCustomerNotifications();
            new NotificationsDialog(this, "Customer Notifications", notifs);
        });

        // --- NEW: Settings button for user profile (edit username/password/name) ---
        JButton settingsBtn = new JButton("⚙ Settings");
        settingsBtn.addActionListener(e -> new UserSettingsDialog(this, this.customerName, ordersFile, orderItemsFile, transactionsFile));

        JPanel bagButtons = new JPanel();
        bagButtons.add(plusBtn);
        bagButtons.add(minusBtn);
        bagButtons.add(finishBtn);
        bagButtons.add(notifBtn);
        bagButtons.add(settingsBtn); // <-- added here

        JPanel bagPanel = new JPanel(new BorderLayout());
        bagPanel.add(new JScrollPane(bagTable), BorderLayout.CENTER);
        bagPanel.add(bagButtons, BorderLayout.SOUTH);

        categoryTabs.add("My Bag", bagPanel);

        add(categoryTabs);
        setVisible(true);
    }

    private JPanel createCategoryPanel(String category) {
        DefaultTableModel model = new DefaultTableModel(new String[]{"Item", "Price"}, 0);
        JTable table = new JTable(model);
        loadCategoryItems(model, category);

        JButton addBtn = new JButton("Add to Bag");
        addBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row != -1) {
                String item = (String) model.getValueAt(row, 0);
                double price = Double.parseDouble((String) model.getValueAt(row, 1));
                addToBag(item, price);
            }
        });

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        panel.add(addBtn, BorderLayout.SOUTH);

        return panel; // always returns a JPanel
    }

    private void loadCategoryItems(DefaultTableModel model, String category) {
        model.setRowCount(0);
        if (menuFile.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(menuFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] parts = line.split(",");
                    if (parts.length == 3 && parts[2].equalsIgnoreCase(category)) {
                        model.addRow(new String[]{parts[0], parts[1]});
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void addToBag(String item, double price) {
        for (int i = 0; i < bagModel.getRowCount(); i++) {
            if (bagModel.getValueAt(i, 0).equals(item)) {
                int qty = (int) bagModel.getValueAt(i, 2);
                bagModel.setValueAt(qty + 1, i, 2);
                return;
            }
        }
        bagModel.addRow(new Object[]{item, price, 1});
    }

    private void finishOrder() {
        if (bagModel.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this, "Your bag is empty!");
            return;
        }

        double total = 0;
        StringBuilder receipt = new StringBuilder("Receipt for " + customerName + "\n");
        for (int i = 0; i < bagModel.getRowCount(); i++) {
            String item = (String) bagModel.getValueAt(i, 0);
            double price = Double.parseDouble(bagModel.getValueAt(i, 1).toString());
            int qty = (int) bagModel.getValueAt(i, 2);
            double lineTotal = price * qty;
            total += lineTotal;
            receipt.append(item).append(" x").append(qty).append(" - ").append(String.format(Locale.US, "%.2f", lineTotal)).append("\n");
        }
        receipt.append("TOTAL: ").append(String.format(Locale.US, "%.2f", total)).append("\n");

        // Legacy files
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(salesFile, true))) {
            bw.write(customerName + "," + String.format(Locale.US, "%.2f", total));
            bw.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(transactionsFile, true))) {
            bw.write(receipt.toString());
            bw.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Structured analytics files
        String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        // orders.csv: datetime,customer,total
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(ordersFile, true))) {
            bw.write(now + "," + escape(customerName) + "," + String.format(Locale.US, "%.2f", total));
            bw.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // order_items.csv: datetime,item,qty,line_total
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(orderItemsFile, true))) {
            for (int i = 0; i < bagModel.getRowCount(); i++) {
                String item = (String) bagModel.getValueAt(i, 0);
                double price = Double.parseDouble(bagModel.getValueAt(i, 1).toString());
                int qty = (int) bagModel.getValueAt(i, 2);
                double lineTotal = price * qty;
                bw.write(now + "," + escape(item) + "," + qty + "," + String.format(Locale.US, "%.2f", lineTotal));
                bw.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Notify admin that a customer placed an order (includes name + total)
        NotificationCenter.addAdminNotification("Customer " + customerName + " placed an order. Total: ₱" + String.format(Locale.US, "%.2f", total));

        JOptionPane.showMessageDialog(this, receipt.toString());
        bagModel.setRowCount(0); // clear bag
    }

    private String escape(String s) {
        // minimal CSV escape for commas/quotes
        String v = s.replace("\"", "\"\"");
        if (v.contains(",") || v.contains("\"")) {
            return "\"" + v + "\"";
        }
        return v;
    }

    // Called by UserSettingsDialog to update UI session after name change
    public void updateCustomerName(String newName) {
        this.customerName = newName;
        setTitle("Customer Menu - " + customerName);
    }
}

// ---------------- ANALYTICS PANEL (Top Sellers + Multi-Series Sales Graph) ----------------
class AnalyticsPanel extends JPanel {
    private final File ordersFile;       // datetime,customer,total
    private final File orderItemsFile;   // datetime,item,qty,line_total

    private final JTable topTable;
    private final DefaultTableModel topModel;

    private final JTable summaryTable;
    private final DefaultTableModel summaryModel;

    private final JComboBox<String> periodBox;
    private final JButton refreshBtn;

    private final SimpleLineChartPanel chartPanel;

    public AnalyticsPanel(File ordersFile, File orderItemsFile) {
        this.ordersFile = ordersFile;
        this.orderItemsFile = orderItemsFile;

        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Left: Top Selling Items
        topModel = new DefaultTableModel(new String[]{"Item", "Qty Sold"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public Class<?> getColumnClass(int columnIndex) {
                return columnIndex == 1 ? Integer.class : String.class;
            }
        };
        topTable = new JTable(topModel);
        JScrollPane leftScroll = new JScrollPane(topTable);
        leftScroll.setPreferredSize(new Dimension(350, 0));
        add(leftScroll, BorderLayout.WEST);

        // Right: Controls + Chart + Summary
        JPanel right = new JPanel(new BorderLayout(8, 8));
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controls.add(new JLabel("View:"));
        periodBox = new JComboBox<>(new String[]{"Daily", "Weekly", "Monthly"});
        controls.add(periodBox);
        refreshBtn = new JButton("Refresh");
        controls.add(refreshBtn);
        right.add(controls, BorderLayout.NORTH);

        chartPanel = new SimpleLineChartPanel();
        right.add(chartPanel, BorderLayout.CENTER);

        // Summary table (history per period)
        summaryModel = new DefaultTableModel();
        summaryTable = new JTable(summaryModel);
        JScrollPane summaryScroll = new JScrollPane(summaryTable);
        summaryScroll.setPreferredSize(new Dimension(0, 150));
        right.add(summaryScroll, BorderLayout.SOUTH);

        add(right, BorderLayout.CENTER);

        // Load initial data
        refreshTopSellers();
        refreshChartAndSummary();

        refreshBtn.addActionListener(e -> {
            refreshTopSellers();
            refreshChartAndSummary();
        });
        periodBox.addActionListener(e -> refreshChartAndSummary());
    }

    private void refreshTopSellers() {
        Map<String, Integer> counts = new HashMap<>();
        if (orderItemsFile.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(orderItemsFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    // datetime,item,qty,line_total
                    String[] p = parseCsvLine(line);
                    if (p.length >= 4) {
                        String item = unescape(p[1]);
                        int qty = parseIntSafe(p[2], 0);
                        counts.put(item, counts.getOrDefault(item, 0) + qty);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        List<Map.Entry<String, Integer>> top = counts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(10)
                .collect(Collectors.toList());

        topModel.setRowCount(0);
        for (Map.Entry<String, Integer> e : top) {
            topModel.addRow(new Object[]{e.getKey(), e.getValue()});
        }
    }

    private void refreshChartAndSummary() {
    	String selected = (String) periodBox.getSelectedItem();
    	final String view = (selected == null) ? "Daily" : selected;

        // bucketsCustomer: label -> (customer -> total)
        LinkedHashMap<String, LinkedHashMap<String, Double>> bucketsCustomer = new LinkedHashMap<>();

        // Gather all orders and bucket them
        if (ordersFile.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(ordersFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    // datetime,customer,total
                    String[] p = parseCsvLine(line);
                    if (p.length >= 3) {
                        LocalDateTime dt = parseDateSafe(p[0]);
                        String customer = unescape(p[1]);
                        double total = parseDoubleSafe(p[2], 0);

                        String key = bucketKey(dt, view);
                        bucketsCustomer.putIfAbsent(key, new LinkedHashMap<>());
                        LinkedHashMap<String, Double> perCust = bucketsCustomer.get(key);
                        perCust.put(customer, perCust.getOrDefault(customer, 0.0) + total);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Sort labels chronologically
        List<String> labels = new ArrayList<>(bucketsCustomer.keySet());
        labels.sort(Comparator.comparing(a -> sortKey(a, view)));

        // Compute grand totals per period and overall totals per customer
        Map<String, Double> grandTotalsByLabel = new LinkedHashMap<>();
        Map<String, Double> totalsPerCustomer = new HashMap<>();
        for (String label : labels) {
            double sum = 0.0;
            for (Map.Entry<String, Double> e : bucketsCustomer.get(label).entrySet()) {
                sum += e.getValue();
                totalsPerCustomer.put(e.getKey(), totalsPerCustomer.getOrDefault(e.getKey(), 0.0) + e.getValue());
            }
            grandTotalsByLabel.put(label, sum);
        }

        // Pick top customers (limit to avoid clutter) + include TOTAL
        int MAX_CUSTOMERS = 6; // show up to 6 customers + TOTAL
        List<String> topCustomers = totalsPerCustomer.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .map(Map.Entry::getKey)
                .limit(MAX_CUSTOMERS)
                .collect(Collectors.toList());

        // Build series map: customer -> values per label
        LinkedHashMap<String, List<Double>> series = new LinkedHashMap<>();
        for (String cust : topCustomers) {
            List<Double> vals = new ArrayList<>();
            for (String label : labels) {
                vals.add(bucketsCustomer.get(label).getOrDefault(cust, 0.0));
            }
            series.put(cust, vals);
        }
        // TOTAL series last
        List<Double> totalVals = labels.stream().map(grandTotalsByLabel::get).collect(Collectors.toList());
        series.put("TOTAL", totalVals);

        chartPanel.setMultiSeries("Sales (" + view + ")", labels, series, "₱");

        // Build summary table: Period | TOTAL | top customer columns
        List<String> cols = new ArrayList<>();
        cols.add("Period");
        cols.add("TOTAL");
        cols.addAll(topCustomers);
        summaryModel.setColumnCount(0);
        summaryModel.setRowCount(0);
        for (String c : cols) summaryModel.addColumn(c);

        for (int i = 0; i < labels.size(); i++) {
            List<Object> row = new ArrayList<>();
            row.add(labels.get(i));
            row.add(String.format(Locale.US, "%,.2f", totalVals.get(i)));
            for (String cust : topCustomers) {
                double v = series.get(cust).get(i);
                row.add(String.format(Locale.US, "%,.2f", v));
            }
            summaryModel.addRow(row.toArray());
        }
    }

    private String bucketKey(LocalDateTime dt, String view) {
        if (view == null) view = "Daily";
        switch (view) {
            case "Daily":
                return dt.toLocalDate().toString(); // yyyy-MM-dd
            case "Weekly": {
                WeekFields wf = WeekFields.of(Locale.getDefault());
                int week = dt.get(wf.weekOfWeekBasedYear());
                int year = dt.get(wf.weekBasedYear());
                return year + "-W" + String.format("%02d", week);
            }
            case "Monthly":
                return dt.getYear() + "-" + String.format("%02d", dt.getMonthValue()); // yyyy-MM
            default:
                return dt.toLocalDate().toString();
        }
    }

    private String sortKey(String label, String view) {
        try {
            switch (view) {
                case "Daily":
                    return label; // yyyy-MM-dd sorts fine as string
                case "Monthly":
                    return label; // yyyy-MM sorts fine as string
                case "Weekly":
                    String[] p = label.split("-W");
                    return p[0] + String.format("%02d", Integer.parseInt(p[1]));
            }
        } catch (Exception ignored) {}
        return label;
    }

    // --- Minimal CSV helpers (handle quotes) ---
    private String[] parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '\"') {
                    sb.append('\"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                out.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        out.add(sb.toString());
        return out.toArray(new String[0]);
    }

    private String unescape(String s) {
        if (s.startsWith("\"") && s.endsWith("\"")) {
            String inner = s.substring(1, s.length() - 1);
            return inner.replace("\"\"", "\"");
        }
        return s;
    }

    private LocalDateTime parseDateSafe(String s) {
        try { return LocalDateTime.parse(s); } catch (Exception e) { return LocalDateTime.now(); }
    }
    private int parseIntSafe(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }
    private double parseDoubleSafe(String s, double def) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return def; }
    }
}

//---------------- Simple Line Chart (no external libs) ----------------
class SimpleLineChartPanel extends JPanel {
    // single-series (backward compatible)
    private List<String> labels = new ArrayList<>();
    private List<Double> values = new ArrayList<>();
    private String seriesName = "";
    private String currencyPrefix = "";

    // multi-series
    private boolean multi = false;
    private LinkedHashMap<String, List<Double>> seriesMap = new LinkedHashMap<>();
    private List<Color> palette;

    public SimpleLineChartPanel() {
        setPreferredSize(new Dimension(600, 400));
        setBackground(Color.WHITE);
        palette = defaultPalette();
    }

    // old API (kept)
    public void setSeries(String name, List<String> labels, List<Double> values, String currencyPrefix) {
        this.multi = false;
        this.seriesName = name == null ? "" : name;
        this.labels = new ArrayList<>(labels == null ? Collections.emptyList() : labels);
        this.values = new ArrayList<>(values == null ? Collections.emptyList() : values);
        this.currencyPrefix = currencyPrefix == null ? "" : currencyPrefix;
        repaint();
    }

    // new API (multi-series per customer + TOTAL)
    public void setMultiSeries(String name, List<String> labels, LinkedHashMap<String, List<Double>> seriesMap, String currencyPrefix) {
        this.multi = true;
        this.seriesName = name == null ? "" : name;
        this.labels = new ArrayList<>(labels == null ? Collections.emptyList() : labels);
        this.seriesMap = new LinkedHashMap<>(seriesMap == null ? new LinkedHashMap<>() : seriesMap);
        this.currencyPrefix = currencyPrefix == null ? "" : currencyPrefix;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        int padLeft = 70;
        int padRight = 30;
        int padTop = 50;   // extra for legend
        int padBottom = 80;

        // Title
        g.setFont(g.getFont().deriveFont(Font.BOLD, 14f));
        g.setColor(Color.DARK_GRAY);
        g.drawString(seriesName, padLeft, padTop - 30);

        // Axes
        int x0 = padLeft;
        int y0 = h - padBottom;
        int x1 = w - padRight;
        int y1 = padTop;

        g.setColor(Color.GRAY);
        g.drawLine(x0, y0, x1, y0); // X axis
        g.drawLine(x0, y0, x0, y1); // Y axis

        // Early exit if no data
        if ((!multi && (values == null || values.isEmpty())) ||
            (multi && (seriesMap == null || seriesMap.isEmpty()))) {
            g.setColor(Color.DARK_GRAY);
            g.drawString("No data", x0 + 10, y0 - 10);
            return;
        }

        // Determine max
        double max = 1.0;
        if (multi) {
            for (List<Double> vs : seriesMap.values()) {
                for (Double v : vs) max = Math.max(max, v == null ? 0 : v);
            }
        } else {
            for (Double v : values) max = Math.max(max, v == null ? 0 : v);
        }
        if (max == 0) max = 1.0;

        int points = multi ? (labels == null ? 0 : labels.size()) : (values == null ? 0 : values.size());
        int plotWidth = x1 - x0;
        int plotHeight = y0 - y1;

        // Gridlines + Y labels (5)
        g.setFont(g.getFont().deriveFont(11f));
        g.setColor(new Color(230, 230, 230));
        for (int i = 0; i <= 5; i++) {
            int y = y0 - (i * plotHeight / 5);
            g.drawLine(x0, y, x1, y);
            double v = (max * i / 5.0);
            g.setColor(Color.DARK_GRAY);
            g.drawString(currencyPrefix + String.format(Locale.US, "%,.0f", v), 5, y + 4);
            g.setColor(new Color(230, 230, 230));
        }

        // X labels + vertical guide lines
        int step = Math.max(1, points / 10);
        g.setColor(Color.DARK_GRAY);
        for (int i = 0; i < points; i += step) {
            int x = x0 + (int) (plotWidth * (i / (double) Math.max(1, points - 1)));
            g.drawLine(x, y0, x, y0 + 4); // tick
            String label = labels.get(i);
            String shortLabel = label.length() > 10 ? label.substring(0, 10) + "…" : label;
            drawRotate(g, shortLabel, x - 10, y0 + 22, -45);

            // vertical guide
            g.setColor(Color.LIGHT_GRAY);
            g.drawLine(x, y0, x, y1);
            g.setColor(Color.DARK_GRAY);
        }

        // Draw lines
        if (!multi) {
            g.setStroke(new BasicStroke(2f));
            g.setColor(new Color(30, 144, 255)); // single series color
            int prevX = -1, prevY = -1;
            for (int i = 0; i < points; i++) {
                double v = values.get(i);
                int x = x0 + (int) (plotWidth * (i / (double) Math.max(1, points - 1)));
                int y = y0 - (int) ((v / max) * plotHeight);
                if (prevX != -1) g.drawLine(prevX, prevY, x, y);
                g.fillOval(x - 3, y - 3, 6, 6);
                prevX = x; prevY = y;
            }
        } else {
            // Draw each series with its own color; TOTAL last and thicker
            int si = 0;
            for (Map.Entry<String, List<Double>> e : seriesMap.entrySet()) {
                String name = e.getKey();
                List<Double> vs = e.getValue();
                Color c = name.equals("TOTAL") ? Color.BLACK : palette.get(si % palette.size());
                float stroke = name.equals("TOTAL") ? 3.0f : 2.0f;

                g.setColor(c);
                g.setStroke(new BasicStroke(stroke));
                int prevX = -1, prevY = -1;
                for (int i = 0; i < points; i++) {
                    double v = (i < vs.size() && vs.get(i) != null) ? vs.get(i) : 0.0;
                    int x = x0 + (int) (plotWidth * (i / (double) Math.max(1, points - 1)));
                    int y = y0 - (int) ((v / max) * plotHeight);
                    if (prevX != -1) g.drawLine(prevX, prevY, x, y);
                    g.fillOval(x - 3, y - 3, 6, 6);
                    prevX = x; prevY = y;
                }
                si++;
            }

            // Legend
            drawLegend(g, x0, y1 - 10, seriesMap.keySet());
        }
    }

    private void drawLegend(Graphics2D g, int xLeft, int yTop, Collection<String> names) {
        int x = xLeft;
        int y = yTop;
        int box = 10;
        int gap = 8;
        int colGap = 20;

        int si = 0;
        for (String name : names) {
            Color c = name.equals("TOTAL") ? Color.BLACK : palette.get(si % palette.size());
            g.setColor(c);
            g.fillRect(x, y, box, box);
            g.setColor(Color.DARK_GRAY);
            g.drawRect(x, y, box, box);
            g.drawString(name, x + box + 6, y + box - 1);
            x += (box + 6 + g.getFontMetrics().stringWidth(name) + colGap);
            si++;
            // wrap if reaching right edge
            if (x > getWidth() - 150) {
                x = xLeft;
                y += box + gap;
            }
        }
    }

    private List<Color> defaultPalette() {
        return Arrays.asList(
                new Color(33, 150, 243),  // blue
                new Color(244, 67, 54),   // red
                new Color(76, 175, 80),   // green
                new Color(255, 152, 0),   // orange
                new Color(156, 39, 176),  // purple
                new Color(0, 188, 212),   // cyan
                new Color(121, 85, 72),   // brown
                new Color(63, 81, 181),   // indigo
                new Color(205, 220, 57),  // lime
                new Color(0, 150, 136)    // teal
        );
    }

    private void drawRotate(Graphics2D g2, String text, int x, int y, int angle) {
        g2.translate(x, y);
        g2.rotate(Math.toRadians(angle));
        g2.drawString(text, 0, 0);
        g2.rotate(-Math.toRadians(angle));
        g2.translate(-x, -y);
    }
}

// ---------------- USER SETTINGS DIALOG (with Order History tab) ----------------
class UserSettingsDialog extends JDialog {
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JTextField firstField, middleField, lastField;
    private JButton saveBtn, cancelBtn;

    private String currentUsername;
    private CashierPanel parentPanel;
    private final File ordersFile;
    private final File orderItemsFile;
    private final File transactionsFile;

    public UserSettingsDialog(CashierPanel parent, String currentUsername, File ordersFile, File orderItemsFile, File transactionsFile) {
        super(parent, "User Settings", true);
        this.currentUsername = currentUsername;
        this.parentPanel = parent;
        this.ordersFile = ordersFile;
        this.orderItemsFile = orderItemsFile;
        this.transactionsFile = transactionsFile;

        setSize(700, 480);
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JTabbedPane tabs = new JTabbedPane();

        // --- Account Settings Tab ---
        JPanel acct = new JPanel(new GridLayout(6, 2, 8, 8));
        acct.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        acct.add(new JLabel("Current Username:"));
        acct.add(new JLabel(currentUsername));

        acct.add(new JLabel("New Username:"));
        usernameField = new JTextField(currentUsername);
        acct.add(usernameField);

        acct.add(new JLabel("New Password (leave empty to keep):"));
        passwordField = new JPasswordField();
        acct.add(passwordField);

        acct.add(new JLabel("First Name:"));
        firstField = new JTextField();
        acct.add(firstField);

        acct.add(new JLabel("Middle Name:"));
        middleField = new JTextField();
        acct.add(middleField);

        acct.add(new JLabel("Last Name:"));
        lastField = new JTextField();
        acct.add(lastField);

        // load existing profile values if present
        String[] prof = LoginScreen.profiles.getOrDefault(currentUsername, new String[]{"", "", ""});
        firstField.setText(prof[0]);
        middleField.setText(prof[1]);
        lastField.setText(prof[2]);

        JPanel acctButtons = new JPanel();
        saveBtn = new JButton("Save Changes");
        cancelBtn = new JButton("Cancel");
        JButton logoutBtn = new JButton("Logout");

        acctButtons.add(saveBtn);
        acctButtons.add(cancelBtn);
        acctButtons.add(logoutBtn);

        // --- Logout button action ---
        logoutBtn.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to log out?",
                "Confirm Logout",
                JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                dispose(); // close settings dialog
                SwingUtilities.getWindowAncestor(parentPanel).dispose(); // close customer window
                SwingUtilities.invokeLater(() -> new LoginScreen());
            }
        });


        acctButtons.add(saveBtn);
        acctButtons.add(cancelBtn);
        acctButtons.add(logoutBtn);

        // --- Logout button action ---
        logoutBtn.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to log out?",
                "Confirm Logout",
                JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                dispose(); // close settings dialog
                SwingUtilities.getWindowAncestor(parentPanel).dispose(); // close cashier/customer window
                new LoginScreen(); // go back to login screen
            }
        });


        JPanel acctWrap = new JPanel(new BorderLayout());
        acctWrap.add(acct, BorderLayout.CENTER);
        acctWrap.add(acctButtons, BorderLayout.SOUTH);

        tabs.add("Account Settings", acctWrap);

        // --- Order History Tab ---
        JPanel histPanel = new JPanel(new BorderLayout(8, 8));
        histPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        DefaultTableModel histModel = new DefaultTableModel(new String[]{"Datetime", "Total"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable histTable = new JTable(histModel);
        JScrollPane histScroll = new JScrollPane(histTable);
        histPanel.add(histScroll, BorderLayout.CENTER);

        JPanel histButtons = new JPanel();
        JButton refreshHist = new JButton("Refresh");
        JButton viewItems = new JButton("View Items");
        histButtons.add(refreshHist);
        histButtons.add(viewItems);
        histPanel.add(histButtons, BorderLayout.SOUTH);

        tabs.add("Order History", histPanel);

        add(tabs);

        // Action listeners
        cancelBtn.addActionListener(e -> dispose());
        saveBtn.addActionListener(e -> saveChanges());

        // refresh history initially
        refreshHist.addActionListener(e -> loadOrderHistory(histModel, currentUsername));
        refreshHist.doClick(); // load once to populate table

        viewItems.addActionListener(e -> {
            int sel = histTable.getSelectedRow();
            if (sel == -1) {
                JOptionPane.showMessageDialog(this, "Select an order first.");
                return;
            }
            String datetime = (String) histModel.getValueAt(sel, 0);
            showOrderItems(datetime);
        });

        setVisible(true);
    }

    private void saveChanges() {
        String newUsername = usernameField.getText().trim();
        String newPassword = String.valueOf(passwordField.getPassword()).trim();
        String first = firstField.getText().trim();
        String middle = middleField.getText().trim();
        String last = lastField.getText().trim();

        if (newUsername.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Username cannot be empty!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Ensure current user exists in memory
        if (!LoginScreen.customers.containsKey(currentUsername)) {
            JOptionPane.showMessageDialog(this, "Error: Current user not found!");
            return;
        }

        // If only password left empty, keep the old password
        String effectivePassword = newPassword.isEmpty() ? LoginScreen.customers.get(currentUsername) : newPassword;

        // Check if new username already exists (and isn't the same as current)
        if (!newUsername.equals(currentUsername) && LoginScreen.customers.containsKey(newUsername)) {
            JOptionPane.showMessageDialog(this, "Username already exists!");
            return;
        }

        // --- Update in-memory customers map and rewrite customers.txt ---
        LoginScreen.customers.remove(currentUsername);
        LoginScreen.customers.put(newUsername, effectivePassword);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(LoginScreen.customerFile))) {
            for (Map.Entry<String, String> entry : LoginScreen.customers.entrySet()) {
                writer.write(entry.getKey() + ":" + entry.getValue());
                writer.newLine();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Failed to save changes to customers file.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // --- Update profiles map and file (move profile if username changed) ---
        String[] existingProfile = LoginScreen.profiles.getOrDefault(currentUsername, new String[]{"", "", ""});
        // use new values if entered, otherwise keep existing
        String f = first.isEmpty() ? existingProfile[0] : first;
        String m = middle.isEmpty() ? existingProfile[1] : middle;
        String l = last.isEmpty() ? existingProfile[2] : last;
        // remove old
        LoginScreen.profiles.remove(currentUsername);
        LoginScreen.profiles.put(newUsername, new String[]{f, m, l});
        // rewrite profiles file
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(LoginScreen.profilesFile))) {
            for (Map.Entry<String, String[]> e : LoginScreen.profiles.entrySet()) {
                String[] v = e.getValue();
                bw.write(e.getKey() + ":" + v[0] + ":" + v[1] + ":" + v[2]);
                bw.newLine();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Failed to save profile changes.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // If username changed, update any orders.csv entries? **(We will NOT rewrite orders.csv or order_items.csv)**
        // NOTE: We keep historical orders as-is (they reference the old username). That keeps audit trail.
        // If you want to migrate historical orders to the new username, I can add that behavior.

        // Update UI session
        parentPanel.updateCustomerName(newUsername);

        JOptionPane.showMessageDialog(this, "Account updated successfully!");
        NotificationCenter.addCustomerNotification("Account updated for user: " + newUsername);

        dispose();
    }

    private void loadOrderHistory(DefaultTableModel model, String username) {
        model.setRowCount(0);
        if (!ordersFile.exists()) return;
        try (BufferedReader br = new BufferedReader(new FileReader(ordersFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = parseCsvLine(line);
                if (p.length >= 3) {
                    String datetime = p[0];
                    String customer = unescape(p[1]);
                    String total = p[2];
                    if (customer.equals(username)) {
                        model.addRow(new Object[]{datetime, total});
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void showOrderItems(String datetime) {
        DefaultTableModel itemsModel = new DefaultTableModel(new String[]{"Item", "Qty", "Line Total"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        if (orderItemsFile.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(orderItemsFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] p = parseCsvLine(line);
                    // datetime,item,qty,line_total
                    if (p.length >= 4) {
                        String dt = p[0];
                        if (dt.equals(datetime)) {
                            String item = unescape(p[1]);
                            String qty = p[2];
                            String lineTotal = p[3];
                            itemsModel.addRow(new Object[]{item, qty, lineTotal});
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        JTable t = new JTable(itemsModel);
        JScrollPane sp = new JScrollPane(t);
        JDialog dlg = new JDialog(this, "Items for " + datetime, true);
        dlg.setSize(500, 300);
        dlg.setLocationRelativeTo(this);
        dlg.add(sp, BorderLayout.CENTER);
        JButton close = new JButton("Close");
        close.addActionListener(e -> dlg.dispose());
        dlg.add(close, BorderLayout.SOUTH);
        dlg.setVisible(true);
    }

    // minimal CSV helpers (same logic used elsewhere)
    private String[] parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '\"') {
                    sb.append('\"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                out.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        out.add(sb.toString());
        return out.toArray(new String[0]);
    }

    private String unescape(String s) {
        if (s.startsWith("\"") && s.endsWith("\"")) {
            String inner = s.substring(1, s.length() - 1);
            return inner.replace("\"\"", "\"");
        }
        return s;
    }
}
