import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Locale;
import java.util.Properties;

/** Local operator only. No public role or status elevation endpoint. */
class UserAdmin {
    public static void main(String[] args) throws Exception {
        if (args.length != 3 || !args[0].matches("[a-zA-Z0-9_]{3,32}")) {
            throw new IllegalArgumentException("Usage: UserAdmin.java username role CUSTOMER|MERCHANT OR username status ENABLED|DISABLED");
        }
        boolean role = args[1].equals("role") && (args[2].equals("CUSTOMER") || args[2].equals("MERCHANT"));
        boolean status = args[1].equals("status") && (args[2].equals("ENABLED") || args[2].equals("DISABLED"));
        if (!role && !status) throw new IllegalArgumentException("Unknown operation or value");
        var settings = new Properties();
        try (var reader = Files.newBufferedReader(Path.of("config/db.local.properties"), StandardCharsets.UTF_8)) { settings.load(reader); }
        String sql = role ? "UPDATE user_account SET role=?,version=version+1,updated_at=UTC_TIMESTAMP(6) WHERE username=?"
                : "UPDATE user_account SET status=?,version=version+1,updated_at=UTC_TIMESTAMP(6) WHERE username=?";
        try (var connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/seed_assistant?connectionTimeZone=UTC&connectTimeout=2000&socketTimeout=3000",
                settings.getProperty("spring.datasource.username"), settings.getProperty("spring.datasource.password"));
             var statement = connection.prepareStatement(sql)) {
            statement.setString(1, args[2]); statement.setString(2, args[0].toLowerCase(Locale.ROOT));
            if (statement.executeUpdate() != 1) throw new IllegalStateException("User not found. Register the intended account first.");
            System.out.println("Account updated. New requests read the current role and status from MySQL.");
        } catch (java.sql.SQLException error) {
            System.err.println("Account update failed. SQLState=" + error.getSQLState() + ", code=" + error.getErrorCode());
            System.exit(1);
        }
    }
}
