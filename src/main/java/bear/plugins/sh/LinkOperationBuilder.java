package bear.plugins.sh;

import bear.core.SessionContext;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class LinkOperationBuilder extends CopyOperationBuilder {
    public LinkOperationBuilder(SessionContext $, String dest) {
        super($, CopyCommandType.LINK, null);
        this.dest = dest;

    }

    public LinkOperationBuilder toSource(String existingSource){
        src = existingSource;
        return this;
    }
}
