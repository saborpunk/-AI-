import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Properties;

/** Project-local SQL runner. Never starts/stops MySQL or handles physical data files. */
class DatabaseSetup {
    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !(args[0].equals("check") || args[0].equals("initialize") || args[0].equals("initialize-v1") || args[0].equals("initialize-v2"))) {
            throw new IllegalArgumentException("Usage: DatabaseSetup.java check|initialize|initialize-v1|initialize-v2 (run at project root)");
        }
        var settings = new Properties();
        try (var reader = Files.newBufferedReader(Path.of("config/db.local.properties"), StandardCharsets.UTF_8)) {
            settings.load(reader);
        }
        String user = settings.getProperty("spring.datasource.username");
        String password = settings.getProperty("spring.datasource.password");
        if (user == null || password == null || user.startsWith("REPLACE_") || password.startsWith("REPLACE_")) {
            throw new IllegalStateException("Fill config/db.local.properties first; do not print passwords.");
        }
        String url = "jdbc:mysql://localhost:3306/?connectTimeout=2000&socketTimeout=5000&connectionTimeZone=UTC";
        try (var connection = DriverManager.getConnection(url, user, password)) {
            System.out.println("Connected to localhost:3306, MySQL " + connection.getMetaData().getDatabaseProductVersion());
            if (args[0].equals("initialize-v2")) {
                executeScript(connection, "003_create_user_account.sql");
                // MySQL DDL commits implicitly. A single ALTER adds column, index and FK atomically.
                boolean exists;
                try (var columns = connection.getMetaData().getColumns("seed_assistant", null, "consultation_session", "user_id")) {
                    exists = columns.next();
                }
                if (!exists) executeScript(connection, "004_add_session_owner.sql");
                boolean foreignKey = false;
                try (var keys = connection.getMetaData().getImportedKeys("seed_assistant", null, "consultation_session")) {
                    while (keys.next()) {
                        if ("user_id".equals(keys.getString("FKCOLUMN_NAME"))
                                && "user_account".equals(keys.getString("PKTABLE_NAME"))
                                && "id".equals(keys.getString("PKCOLUMN_NAME"))) foreignKey = true;
                    }
                }
                if (!foreignKey) throw new IllegalStateException("Existing user_id has no expected foreign key. Review schema before retrying.");
                System.out.println("V2 schema ready. Existing sessions remain unassigned. No accounts seeded.");
            } else if (!args[0].equals("check")) {
                String script = args[0].equals("initialize-v1") ? "002_create_traditional_business.sql" : "001_create_consultation.sql";
                executeScript(connection, script);
                System.out.println("Project SQL initialization completed; existing rows preserved.");
            }
        } catch (java.sql.SQLException error) {
            System.err.println("Database operation failed on localhost:3306. SQLState="
                    + error.getSQLState() + ", errorCode=" + error.getErrorCode()
                    + ". Check the existing service, account permissions and project SQL; no direct data-file operations were performed.");
            System.exit(1);
        }
    }

    private static void executeScript(java.sql.Connection connection, String script) throws Exception {
        String sql = Files.readString(Path.of("scripts/sql/" + script), StandardCharsets.UTF_8);
        try (var statement = connection.createStatement()) {
            for (String part : sql.split(";")) {
                if (!part.isBlank()) statement.execute(part);
            }
        }
    }
}
