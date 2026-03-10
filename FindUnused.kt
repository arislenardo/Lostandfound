import java.io.File
import java.util.regex.Pattern

fun main() {
    val dir = File("app/src/main/java/com/example/lostandfound")
    
    dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
        val content = file.readText()
        
        // Find all imports
        val importMatcher = Pattern.compile("^import\\s+([\\w\\.]+)", Pattern.MULTILINE).matcher(content)
        val imports = mutableListOf<String>()
        while (importMatcher.find()) {
            imports.add(importMatcher.group(1))
        }
        
        // Remove import lines to search the body
        val body = content.replace(Regex("^import\\s+.*$", RegexOption.MULTILINE), "")
        
        val unused = mutableListOf<String>()
        for (imp in imports) {
            if (imp.endsWith("*")) continue
            
            val name = imp.substringAfterLast(".")
            
            // Skip common compose operators
            if (name == "getValue" || name == "setValue") continue
            
            // Search for the word boundary
            val usagePattern = Pattern.compile("\\b${Pattern.quote(name)}\\b")
            if (!usagePattern.matcher(body).find()) {
                unused.add(imp)
            }
        }
        
        if (unused.isNotEmpty()) {
            println("${file.name}:")
            unused.forEach { println("  - $it") }
        }
    }
}
