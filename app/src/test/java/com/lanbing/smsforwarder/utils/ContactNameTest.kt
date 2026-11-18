package com.lanbing.smsforwarder.utils

import org.junit.Assert.*
import org.junit.Test

class ContactNameTest {
    private val number = "13800138000"
    private fun params(name: String? = "张三") = MessageTemplateRenderer.TemplateParams(number, "你好", contactName = name)

    @Test fun chineseCountryCodeAndFormattingMatchTheLocalNumber() {
        assertTrue(ContactIdentity.numbersMatch("+86 138-0013-8000", number))
        assertTrue(ContactIdentity.numbersMatch("008613800138000", number))
        assertTrue(ContactIdentity.numbersMatch("8613800138000", number))
    }
    @Test fun canadianInternationalNumberMatchesTenDigitContact() {
        assertTrue(ContactIdentity.numbersMatch("+1 (416) 555-1234", "4165551234"))
        assertTrue(ContactIdentity.numbersMatch("+14165551234", "14165551234"))
    }
    @Test fun unrelatedCountriesAndSuffixMatchesAreRejected() {
        assertFalse(ContactIdentity.numbersMatch("+441234567890", "+331234567890"))
        assertFalse(ContactIdentity.numbersMatch(number, "8000"))
        assertFalse(ContactIdentity.numbersMatch("+13982468823", "13982468823".let { "+86$it" }))
    }
    @Test fun hiddenAndAlphanumericSendersAreNotPhoneLookupCandidates() {
        listOf("", "未知号码", "PRIVATE", "Bank123", "-1", "-2", "-3", "123+456", "++12345", "*100#").forEach {
            assertNull(it, ContactIdentity.normalizeNumber(it))
        }
    }
    @Test fun exactShortServiceNumberCanHaveContactName() {
        assertEquals("中国移动", ContactIdentity.chooseName("10086", listOf("10086" to "中国移动")))
        assertNull(ContactIdentity.chooseName("10086", listOf("10610086" to "不相关")))
    }
    @Test fun providerLooseSuffixMatchCannotExposeWrongContactName() {
        assertNull(ContactIdentity.chooseName(number, listOf("13900138000" to "另一人")))
    }
    @Test fun ambiguousNamesFallBackToNumber() {
        assertNull(ContactIdentity.chooseName(number, listOf(number to "张三", "+86$number" to "李四")))
        assertEquals("张三", ContactIdentity.chooseName(number, listOf(number to "张三", "+86$number" to "张三")))
    }
    @Test fun blankNameAndUnavailableContactFallBackToOriginalNumber() {
        assertEquals(number, ContactIdentity.label(number, " \n"))
        assertEquals(number, ContactIdentity.label(number, null))
        assertEquals("未知号码", ContactIdentity.label("", null))
    }
    @Test fun defaultSmsUsesOnlyNameAndKeepsRawSenderForRules() {
        val p = params()
        val text = MessageTemplateRenderer.buildDefaultMessage(p, true, false)
        assertEquals("来自: 张三\n你好", text)
        assertFalse(text.contains(number))
        assertEquals(number, p.sender)
        assertTrue(MessageMatcher.passesGlobalSenderFilter(p.sender, listOf("138*"), emptyList()))
    }
    @Test fun hiddenSmsSenderHidesBothNameAndNumber() {
        assertEquals("你好", MessageTemplateRenderer.buildDefaultMessage(params(), false, false))
    }
    @Test fun customSenderPlaceholderUsesNameWithoutAddingNumber() {
        assertEquals("张三：你好", MessageTemplateRenderer.render("{sender}：{content}", params()))
        assertEquals("$number：你好", MessageTemplateRenderer.render("{sender}：{content}", params(null)))
    }
    @Test fun contactNamesAndSmsContentsCannotExpandTemplatePlaceholdersAgain() {
        val p = params("张{content}").copy(content = "保留{sender}与{code}")
        assertEquals("张{content}|保留{sender}与{code}", MessageTemplateRenderer.render("{sender}|{content}", p))
    }
    @Test fun nameWithControlCharactersCannotCreateExtraNotificationFields() {
        assertEquals("张三 备注", ContactIdentity.label(number, "张三\n备注"))
    }
    @Test fun incomingAndMissedCallsShowOnlyTheContactName() {
        CallAlertKind.values().forEach { kind ->
            val text = formatCallAlert(CallAlert(kind, number, 1L), "12:00", "Phone", "SIM1", "张三")
            assertTrue(text.contains("来电人：张三"))
            assertFalse(text.contains(number))
        }
    }
    @Test fun delayedNumberSupplementAlsoUsesOnlyContactName() {
        val text = formatCallNumberSupplement(CallAlert(CallAlertKind.INCOMING, number, 1L), "12:00", "SIM2", "张三")
        assertTrue(text.contains("来电信息补充"))
        assertTrue(text.contains("来电人：张三"))
        assertFalse(text.contains(number))
    }
    @Test fun unknownAndUnmatchedCallNumbersStillProduceAlerts() {
        assertTrue(formatCallAlert(CallAlert(CallAlertKind.INCOMING, number, 1L), "12:00", "", "").contains(number))
        assertTrue(formatCallAlert(CallAlert(CallAlertKind.INCOMING, "", 1L), "12:00", "", "").contains("未知号码"))
    }
}
