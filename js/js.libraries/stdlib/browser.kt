package kotlin.browser

import org.w3c.dom.Document

/**
 * Provides access to the current active browsers DOM for the currently visible page.
 */
native("document") public var document: Document = noImpl

