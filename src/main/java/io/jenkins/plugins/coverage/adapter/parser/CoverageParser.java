package io.jenkins.plugins.coverage.adapter.parser;

import io.jenkins.plugins.coverage.targets.CoverageMetric;
import io.jenkins.plugins.coverage.targets.CoverageResult;
import io.jenkins.plugins.coverage.targets.Ratio;
import org.apache.commons.lang.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parse the standard format coverage report and convert it into {@link CoverageResult}.
 */
public abstract class CoverageParser {

    private static final Pattern CONDITION_COVERAGE_PATTERN = Pattern.compile("(\\d*)\\s*%\\s*\\((\\d*)/(\\d*)\\)");

    private String reportName;

    /**
     * Report name will show in the UI, to differentiate different report.
     *
     * @param reportName name of the report
     */
    public CoverageParser(String reportName) {
        this.reportName = reportName;
    }

    /**
     * Parse coverage report {@link Document} to {@link CoverageResult}.
     *
     * @param document DOM document of coverage report
     * @return Coverage result of specified report
     */
    public CoverageResult parse(Document document) {
        CoverageResult result = processElement(document.getDocumentElement(), null);
        parse(document.getDocumentElement(), result);
        return result;
    }

    /**
     * Iterate the child elements of parent node, and parse the element to {@link CoverageResult}, then make the CoverageResult.
     * be the child of parent CoverageResult.
     *
     * @param parent       parent Node
     * @param parentResult parent coverage result
     */
    public void parse(final Node parent, final CoverageResult parentResult) {
        NodeList nodeList = parent.getChildNodes();
        for (int i = 0, len = nodeList.getLength(); i < len; i++) {
            Node n = nodeList.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE) {
                CoverageResult r = processElement((Element) n, parentResult);
                parse(n, r);
            }
        }
    }

    /**
     * Process DOM {@link Element} and convert it to {@link CoverageResult}.
     *
     * @param current      current element
     * @param parentResult parent coverage result
     * @return coverage result converted from Element
     */
    protected abstract CoverageResult processElement(Element current, CoverageResult parentResult);


    /**
     * Getter for property 'reportName'.
     *
     * @return value for property 'reportName'
     */
    public String getReportName() {
        return reportName;
    }

    /**
     * Setter for property 'reportName'.
     *
     * @param reportName value to set for property 'reportName'
     */
    public void setReportName(String reportName) {
        this.reportName = reportName;
    }

    protected String getAttribute(Element e, String attributeName, String defaultValue) {
        String value = e.getAttribute(attributeName);
        return StringUtils.isEmpty(value) ? defaultValue : value;
    }

    protected void processLine(Element current, CoverageResult parentResult) {
        String hitsString = current.getAttribute("hits");
        String lineNumber = current.getAttribute("number");
        int denominator = 0;
        int numerator = 0;
        if (Boolean.parseBoolean(current.getAttribute("branch"))) {
            final String conditionCoverage = current.getAttribute("condition-coverage");
            if (conditionCoverage != null) {
                // some cases in the wild have branch = true but no condition-coverage attribute

                // should be of the format xxx% (yyy/zzz),
                // or xxx % (yyy/zzz) for French,
                // because cobertura uses the default locale as said in
                // http://sourceforge.net/tracker/?func=detail&aid=3296149&group_id=130558&atid=720015
                Matcher matcher = CONDITION_COVERAGE_PATTERN.matcher(conditionCoverage);
                if (matcher.matches()) {
                    assert matcher.groupCount() == 3;
                    final String numeratorStr = matcher.group(2);
                    final String denominatorStr = matcher.group(3);
                    try {
                        numerator = Integer.parseInt(numeratorStr);
                        denominator = Integer.parseInt(denominatorStr);
                        parentResult.updateMetric(CoverageMetric.CONDITIONAL, Ratio.create(numerator, denominator));
                    } catch (NumberFormatException ignore) {
                    }
                }
            }
        }
        try {
            int hits = Integer.parseInt(hitsString);
            int number = Integer.parseInt(lineNumber);
            if (denominator == 0) {
                parentResult.paint(number, hits);
            } else {
                parentResult.paint(number, hits, numerator, denominator);
            }
            parentResult.updateMetric(CoverageMetric.LINE, Ratio.create((hits == 0) ? 0 : 1, 1));
        } catch (NumberFormatException ignore) {
        }
    }

}
