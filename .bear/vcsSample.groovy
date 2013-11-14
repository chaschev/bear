import bear.core.SessionContext
import bear.vcs.GitCLIPlugin
import bear.vcs.LsResult

class VcsSampleScript extends bear.main.Script {
    GitCLIPlugin git;

    SessionContext _

    @Override
    void run()
    {
        _.putConst(bear.repositoryURI, 'git@github.com:chaschev/grailstwitter.git')
        _.putConst(bear.vcsBranchName, 'master')

        logger.info("git: {}", git)
        def session = git.newSession(_, parent)

        logger.info("head: {}", session.head())

        final LsResult result = session.lsRemote(_.var(bear.vcsBranchName)).run()

        logger.info("lsRemote: {}, {}", result.text, result.files)


//        runner.run(global.tasks.vcsUpdate);
    }
}