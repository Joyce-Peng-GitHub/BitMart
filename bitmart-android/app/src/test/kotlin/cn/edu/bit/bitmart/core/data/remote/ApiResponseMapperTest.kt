package cn.edu.bit.bitmart.core.data.remote

import cn.edu.bit.bitmart.core.domain.DomainResult
import org.junit.Assert.assertEquals
import org.junit.Test

class ApiResponseMapperTest {

    @Test
    fun `parseError extracts code message and structured details`() {
        val body = """
            {"error":{"code":"VALIDATION_FAILED","message":"weak",
              "details":[
                {"field":"password","code":"PASSWORD_TOO_SHORT","params":{"minLength":"8"}},
                {"field":"password","code":"PASSWORD_TOO_SIMPLE","params":{"minCharClasses":"2"}}
              ]}}
        """.trimIndent()
        val r = ApiResponseMapper.parseError(400, body)
        assertEquals("VALIDATION_FAILED", r.code)
        assertEquals(400, r.httpStatus)
        assertEquals(2, r.details.size)
        assertEquals("PASSWORD_TOO_SHORT", r.details[0].code)
        assertEquals("8", r.details[0].params["minLength"])
    }

    @Test
    fun `parseError without details yields empty details`() {
        val body = """{"error":{"code":"CONFLICT","message":"dup"}}"""
        val r = ApiResponseMapper.parseError(409, body)
        assertEquals("CONFLICT", r.code)
        assertEquals(0, r.details.size)
    }

    @Test
    fun `parseError on non-envelope body falls back to HTTP code`() {
        val r = ApiResponseMapper.parseError(500, "not json")
        assertEquals("HTTP_500", r.code)
        assertEquals(0, r.details.size)
    }
}
