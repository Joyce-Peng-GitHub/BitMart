package cn.edu.bit.bitmart.external

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.http.HttpStatusCode

class ShowApiClientTest : FunSpec({

    val base = "https://route.showapi.example"
    val appKey = "test-app-key"

    val successBody = """
        {
          "showapi_res_code": 0,
          "showapi_res_error": "",
          "showapi_res_body": {
            "ret_code": 0,
            "remark": "success",
            "data": {
              "title": "深入理解计算机系统",
              "author": "Randal E. Bryant",
              "publisher": "机械工业出版社",
              "pubdate": "2016-11-01",
              "isbn": "9787111544937",
              "price": "139.00",
              "page": "737",
              "edition": "3",
              "binding": "平装",
              "format": "16开",
              "img": "https://example.com/cover.jpg",
              "gist": "本书是计算机系统领域的经典教材。"
            }
          }
        }
    """.trimIndent()

    test("查询成功 → Found，字段正确映射") {
        val (client, recorder) = MockHttpSupport.jsonClient(body = successBody)
        val result = ShowApiClient(client, base, appKey).lookup("9787111544937")
        result.shouldBeInstanceOf<IsbnLookupResult.Found>()
        result.meta.title shouldBe "深入理解计算机系统"
        result.meta.author shouldBe "Randal E. Bryant"
        result.meta.publisher shouldBe "机械工业出版社"
        result.meta.edition shouldBe "3"
        result.meta.isbn shouldBe "9787111544937"
        result.rawJson shouldBe successBody
        // 校验 appKey 与 isbn 作为查询参数传出。
        val sent = recorder.requests.single()
        sent.url.parameters["appKey"] shouldBe appKey
        sent.url.parameters["isbn"] shouldBe "9787111544937"
    }

    test("ret_code 非 0 → NotFound") {
        val body = """{"showapi_res_code":0,"showapi_res_body":{"ret_code":-1,"remark":"not found"}}"""
        val (client, _) = MockHttpSupport.jsonClient(body = body)
        ShowApiClient(client, base, appKey).lookup("0000000000000") shouldBe IsbnLookupResult.NotFound
    }

    test("缺少 data → NotFound") {
        val body = """{"showapi_res_code":0,"showapi_res_body":{"ret_code":0,"remark":"ok"}}"""
        val (client, _) = MockHttpSupport.jsonClient(body = body)
        ShowApiClient(client, base, appKey).lookup("123") shouldBe IsbnLookupResult.NotFound
    }

    test("顶层 showapi_res_code 非 0 → ServiceError") {
        val body = """{"showapi_res_code":-4,"showapi_res_error":"配额不足"}"""
        val (client, _) = MockHttpSupport.jsonClient(body = body)
        val result = ShowApiClient(client, base, appKey).lookup("123")
        result.shouldBeInstanceOf<IsbnLookupResult.ServiceError>()
    }

    test("HTTP 非 200 → ServiceError") {
        val (client, _) = MockHttpSupport.jsonClient(status = HttpStatusCode.ServiceUnavailable, body = "down")
        val result = ShowApiClient(client, base, appKey).lookup("123")
        result.shouldBeInstanceOf<IsbnLookupResult.ServiceError>()
    }

    test("响应非法 JSON → ServiceError（不抛出）") {
        val (client, _) = MockHttpSupport.jsonClient(body = "not json at all")
        val result = ShowApiClient(client, base, appKey).lookup("123")
        result.shouldBeInstanceOf<IsbnLookupResult.ServiceError>()
    }

    test("缺失字段以 null 表示") {
        val body = """
            {"showapi_res_code":0,"showapi_res_body":{"ret_code":0,"data":{"title":"只有标题"}}}
        """.trimIndent()
        val (client, _) = MockHttpSupport.jsonClient(body = body)
        val result = ShowApiClient(client, base, appKey).lookup("9787000000000")
        result.shouldBeInstanceOf<IsbnLookupResult.Found>()
        result.meta.title shouldBe "只有标题"
        result.meta.author shouldBe null
        // 上游未返回 isbn 时回退到查询参数。
        result.meta.isbn shouldBe "9787000000000"
    }
})
