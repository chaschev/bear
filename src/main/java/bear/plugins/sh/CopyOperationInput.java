package bear.plugins.sh;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class CopyOperationInput extends PermissionsCommandInput<CopyOperationInput>{
    CopyCommandType type;
    String src;
    String dest;

    public CopyOperationInput(CopyCommandType type, String src, String dest) {
        this.type = type;
        this.src = src;
        this.dest = dest;
    }

    public static CopyOperationInput ln(String src, String dest){
        return new CopyOperationInput(CopyCommandType.LINK, src, dest);
    }

    public static CopyOperationInput cp(String src, String dest){
        return new CopyOperationInput(CopyCommandType.COPY, src, dest);
    }

    public static CopyOperationInput mv(String src, String dest){
        return new CopyOperationInput(CopyCommandType.MOVE, src, dest);
    }
}
