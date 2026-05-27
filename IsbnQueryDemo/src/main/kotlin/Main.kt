package cn.bit.edu

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.jsoup.Jsoup

fun main() = runBlocking {
    println("Select query method:")
    println("  1 - ISBNSearch.org (web scraping, no key required)")
    println("  2 - ShowAPI (JSON API, requires appKey)")
    print("Choice [1/2]: ")
    val choice = readlnOrNull()?.trim()

    if (choice != "1" && choice != "2") {
        println("Invalid choice.")
        return@runBlocking
    }

    var appKey = ""
    if (choice == "2") {
        print("Enter ShowAPI appKey: ")
        appKey = readlnOrNull()?.trim().orEmpty()
        if (appKey.isBlank()) {
            println("appKey cannot be empty.")
            return@runBlocking
        }
    }

    print("Enter ISBN: ")
    val isbn = readlnOrNull()?.trim().orEmpty()

    if (!isValidIsbn(isbn)) {
        println("Invalid ISBN. Must be 10 or 13 digits (hyphens allowed).")
        return@runBlocking
    }

    val digits = isbn.replace("-", "")
    println("Querying ISBN $digits ...")

    HttpClient(CIO) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }.use { client ->
        when (choice) {
            "1" -> queryByWebScraping(client, digits)
            "2" -> queryByShowApi(client, digits, appKey)
        }
    }
}

private suspend fun queryByWebScraping(client: HttpClient, isbn: String) {
    val html = try {
        client.get("https://isbnsearch.org/isbn/$isbn").bodyAsText()
    } catch (e: Exception) {
        println("Request failed: the site may be blocking automated access (reCAPTCHA).")
        println("Try again later or use method 2 (ShowAPI) instead.")
        return
    }

    val doc = Jsoup.parse(html)
    val bookInfo = doc.selectFirst("div.bookinfo")

    if (bookInfo == null) {
        if (doc.selectFirst(".g-recaptcha, #recaptcha") != null) {
            println("The site is requesting CAPTCHA verification.")
            println("Cannot proceed with automated query. Try again later or use method 2 (ShowAPI) instead.")
        } else {
            println("No results found for ISBN $isbn.")
        }
        return
    }

    println()
    println("Title: ${bookInfo.selectFirst("h1")?.text() ?: "N/A"}")
    for (p in bookInfo.select("p")) {
        val label = p.selectFirst("strong")?.text()?.removeSuffix(":") ?: continue
        val value = p.text().removePrefix("${label}:").trim()
        println("$label: $value")
    }
}

private suspend fun queryByShowApi(client: HttpClient, isbn: String, appKey: String) {
    val response = client.get("https://route.showapi.com/1626-1") {
        parameter("appKey", appKey)
        parameter("isbn", isbn)
    }

    val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
    val resCode = json["showapi_res_code"]?.jsonPrimitive?.intOrNull
    if (resCode != 0) {
        val error = json["showapi_res_error"]?.jsonPrimitive?.contentOrNull ?: "Unknown error"
        println("API error (code=$resCode): $error")
        return
    }

    val body = json["showapi_res_body"]?.jsonObject ?: run {
        println("Unexpected response format.")
        return
    }

    val retCode = body["ret_code"]?.jsonPrimitive?.intOrNull
    if (retCode != 0) {
        val remark = body["remark"]?.jsonPrimitive?.contentOrNull ?: "Not found"
        println("Query failed: $remark")
        return
    }

    val data = body["data"]?.jsonObject ?: run {
        println("No book data in response.")
        return
    }

    println()
    printField("Title", data["title"])
    printField("Author", data["author"])
    printField("Publisher", data["publisher"])
    printField("Publish Date", data["pubdate"])
    printField("ISBN", data["isbn"])
    printField("Price", data["price"])
    printField("Pages", data["page"])
    printField("Edition", data["edition"])
    printField("Format", data["format"])
    printField("Binding", data["binding"])
    printField("Paper", data["paper"])
    printField("Cover Image", data["img"])
    val gist = data["gist"]?.jsonPrimitive?.contentOrNull
    if (!gist.isNullOrBlank()) {
        println("Summary: ${gist.take(200)}${if (gist.length > 200) "..." else ""}")
    }
}

private fun printField(label: String, element: JsonElement?) {
    val value = element?.jsonPrimitive?.contentOrNull
    if (!value.isNullOrBlank()) println("$label: $value")
}

private fun isValidIsbn(input: String): Boolean {
    val digits = input.replace("-", "")
    if (digits.length != 10 && digits.length != 13) return false
    if (digits.length == 10) return digits.substring(0, 9).all { it.isDigit() } && (digits[9].isDigit() || digits[9].uppercaseChar() == 'X')
    return digits.all { it.isDigit() }
}
