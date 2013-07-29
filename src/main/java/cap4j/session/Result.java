package cap4j.session;

/**
* User: chaschev
* Date: 7/21/13
*/
public enum Result {
    OK, CONNECTION_ERROR, TIMEOUT, ERROR;

    public static Result and(Result... results){
        for (Result result : results) {
            if(result != OK){
                return result;
            }
        }

        return OK;
    }

    public boolean nok() {
        return this != OK;
    }
}
