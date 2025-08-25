package uk.gov.hmrc.dslgen.dslgen.support;

import java.util.List;

public class PrePostFile {
    private List<String> prepend;
    private List<String> append;

    public List<String> getPrepend() { return prepend; }
    public void setPrepend(List<String> prepend) { this.prepend = prepend; }
    public List<String> getAppend() { return append; }
    public void setAppend(List<String> append) { this.append = append; }
}
