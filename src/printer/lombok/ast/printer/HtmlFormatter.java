/*
 * Copyright © 2010 Reinier Zwitserloot and Roel Spilker.
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package lombok.ast.printer;

import static org.apache.commons.lang.StringEscapeUtils.escapeHtml;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.Cleanup;
import lombok.ast.Node;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;

public class HtmlFormatter implements SourceFormatter {
	private final StringBuilder sb = new StringBuilder();
	private final List<String> inserts = new ArrayList<String>();
	private final String rawSource;
	private final List<String> errors = new ArrayList<String>();
	private String nextElementName;
	
	public HtmlFormatter(String rawSource) {
		this.rawSource = rawSource;
	}
	
	@Override public void reportAssertionFailureNext(Node node, String message, Throwable error) {
		inserts.add("<span class=\"assertionError\">" + escapeHtml(message) + "</span>");
	}
	
	@Override public void reportAssertionFailure(Node node, String message, Throwable error) {
		inserts.add("<span class=\"assertionError\">" + escapeHtml(message) + "</span>");
	}
	
	private void handleInserts() {
		for (String insert : inserts) sb.append(insert);
		inserts.clear();
	}
	
	private static final String OPENERS = "{([<", CLOSERS = "})]>";
	private int parenCounter = 0;
	private final ArrayDeque<Integer> parenStack = new ArrayDeque<Integer>();
	
	
	@Override public void fail(String fail) {
		sb.append("<span class=\"fail\">").append(FAIL).append(escapeHtml(fail)).append(FAIL).append("</span>");
	}
	
	@Override public void keyword(String text) {
		sb.append("<span class=\"keyword\">").append(escapeHtml(text)).append("</span>");
	}
	
	@Override public void operator(String text) {
		sb.append("<span class=\"operator\">").append(escapeHtml(text)).append("</span>");
	}
	
	@Override public void verticalSpace() {
		sb.append("<br />");
	}
	
	@Override public void space() {
		sb.append(" ");
	}
	
	@Override public void append(String text) {
		if (text.length() == 1) {
			if (OPENERS.contains(text)) {
				parenCounter++;
				parenStack.push(parenCounter);
				sb.append("<span class=\"open\" id=\"open_").append(parenCounter).append("\">").append(escapeHtml(text)).append("</span>");
				return;
			}
			if (CLOSERS.contains(text)) {
				Integer n = parenStack.poll();
				if (n == null) {
					n = ++parenCounter;
				}
				sb.append("<span class=\"clos\" id=\"clos_").append(n).append("\">").append(escapeHtml(text)).append("</span>");
				return;
			}
		}
		
		sb.append(escapeHtml(text));
	}
	
	@Override public void buildInline(Node node) {
		generateOpenTag(node, "span");
	}
	
	@Override public void closeInline() {
		sb.append("</span>");
	}
	
	@Override public void startSuppressBlock() {
		sb.append("<span class=\"blockSuppress\">");
	}
	
	@Override public void endSuppressBlock() {
		sb.append("</span>");
	}
	
	private static final Pattern HTML_CLASS_SIGNIFICANT_NODE = Pattern.compile("^lombok\\.ast\\.(\\w+)$");
	
	@Override public void buildBlock(Node node) {
		generateOpenTag(node, "div");
	}
	
	private void generateOpenTag(Node node, String tagName) {
		Set<String> classes = new HashSet<String>();
		findHtmlClassSignificantNodes(classes, node == null ? null : node.getClass());
		
		sb.append("<").append(tagName);
		if (!classes.isEmpty()) {
			sb.append(" class=\"").append(StringUtils.join(classes, " ")).append("\"");
		}
		if (nextElementName != null) {
			sb.append(" relation=\"").append(escapeHtml(nextElementName)).append("\"");
			nextElementName = null;
		}
		handleInserts();
	}
	
	private static void findHtmlClassSignificantNodes(Set<String> names, Class<?> c) {
		if (c == null) return;
		Matcher m = HTML_CLASS_SIGNIFICANT_NODE.matcher(c.getName());
		if (m.matches()) names.add(c.getSimpleName());
		findHtmlClassSignificantNodes(names, c.getSuperclass());
		for (Class<?> i : c.getInterfaces()) findHtmlClassSignificantNodes(names, i);
	}
	
	@Override public void closeBlock() {
		sb.append("</div>");
	}
	
	@Override public void addError(int errorStart, int errorEnd, String errorMessage) {
		errors.add(String.format("<div class=\"parseError\">%s</div>", escapeHtml(errorMessage)));
	}
	
	@Override public String finish() throws IOException {
		String template;
		{
			@Cleanup InputStream in = getClass().getResourceAsStream("ast.html");
			template = IOUtils.toString(in, "UTF-8");
		}
		
		return template
				.replace("{{@title}}", "AST nodes")
				.replace("{{@file}}", "source file name goes here")
				.replace("{{@script}}", "")
				.replace("{{@css}}", "")
				.replace("{{@body}}", sb.toString())
				.replace("{{@errors}}", printErrors())
				.replace("{{@rawSource}}", escapeHtml(rawSource))
				.replace("{{@timeTaken}}", "" + timeTaken);
	}
	
	private String printErrors() {
		if (errors.isEmpty()) return "<div class=\"allClear\">No parse errors!</div>";
		StringBuilder sb = new StringBuilder();
		for (String x : errors) {
			sb.append(x);
		}
		return sb.toString();
	}
	
	private String timeTaken = "(Unknown)";
	
	@Override public void setTimeTaken(long taken) {
		timeTaken = taken + " milliseconds.";
	}
	
	@Override public void nameNextElement(String name) {
		assert nextElementName == null;
		nextElementName = name;
	}
}
