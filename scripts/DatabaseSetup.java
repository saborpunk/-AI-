import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Properties;

/** Project-local SQL runner. Never starts/stops MySQL or handles physical data files. */
class DatabaseSetup {
    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !(args[0].equals("check") || args[0].equals("initialize"))) {
            throw new IllegalArgumentException("Usage: DatabaseSetup.java check|initialize (run at project root)");
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
            if (args[0].equals("initialize")) {
                String sql = Files.readString(Path.of("scripts/sql/001_create_consultation.sql"));
                try (var statement = connection.createStatement()) {
                    for (String part : sql.split(";")) {
                        if (!part.isBlank()) { statement.execute(part); }
                    }
                }
                System.out.println("Project SQL initialization completed; existing rows preserved.");
            }
        } catch (java.sql.SQLException error) {
            System.err.println("Database operation failed on localhost:3306. SQLState="
                    + error.getSQLState() + ", errorCode=" + error.getErrorCode()
                    + ". Check the existing service, account permissions and project SQL; no direct data-file operations were performed.");
            System.exit(1);
        }
    }
}
