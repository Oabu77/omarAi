package com.darcloud.omarai.data.local

enum class ProductCapability { BUSINESS, ESTIMATING, COMPANY_BUILDER, GENERAL_ASSISTANT, COMING_LATER }

data class RoutedIntent(
    val agent: String,
    val capability: ProductCapability,
    val supportedInV1: Boolean,
    val explanation: String,
)

object IntentRouter {
    private val deferred = mapOf(
        "Receptionist Agent" to listOf("answer my call", "phone receptionist", "transfer call", "record call"),
        "Finance Agent" to listOf("bank account", "manage my money", "investment", "send money", "subscription spending"),
        "Communication Agent" to listOf("send a message", "video call", "group chat", "encrypted message"),
        "Marketplace Agent" to listOf("find me a plumber", "book a provider", "marketplace", "nearby provider"),
    )

    fun route(text: String, hasPhoto: Boolean): RoutedIntent {
        val normalized = text.lowercase()
        deferred.entries.firstOrNull { (_, phrases) -> phrases.any { phrase -> normalized.contains(phrase) } }?.let {
            return RoutedIntent(
                agent = it.key,
                capability = ProductCapability.COMING_LATER,
                supportedInV1 = false,
                explanation = "That live integration is coming later and no external action was taken.",
            )
        }
        return when {
            hasPhoto || listOf("quote", "estimate", "scope", "materials", "junk").any { normalized.contains(it) } ->
                RoutedIntent("Estimating Agent", ProductCapability.ESTIMATING, true, "Photo and job-estimate planning")
            listOf("start a company", "build a company", "business plan", "company idea").any { normalized.contains(it) } ->
                RoutedIntent("Company Builder Agent", ProductCapability.COMPANY_BUILDER, true, "Company planning and filing preparation only")
            listOf("customer", "lead", "invoice", "job", "business", "revenue", "crm").any { normalized.contains(it) } ->
                RoutedIntent("Business Agent", ProductCapability.BUSINESS, true, "Business planning and local records")
            else -> RoutedIntent("Omar Core Agent", ProductCapability.GENERAL_ASSISTANT, true, "General assistant request")
        }
    }
}
