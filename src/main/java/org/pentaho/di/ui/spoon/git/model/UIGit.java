package org.pentaho.di.ui.spoon.git.model;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.RemoteAddCommand;
import org.eclipse.jgit.api.RemoteRemoveCommand;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.pentaho.di.repository.ObjectId;
import org.pentaho.di.repository.ObjectRevision;
import org.pentaho.di.repository.RepositoryElementMetaInterface;
import org.pentaho.di.repository.RepositoryObject;
import org.pentaho.di.repository.RepositoryObjectType;
import org.pentaho.di.repository.StringObjectId;
import org.pentaho.di.repository.pur.PurObjectRevision;
import org.pentaho.di.ui.repository.pur.repositoryexplorer.model.UIRepositoryObjectRevision;
import org.pentaho.di.ui.repository.pur.repositoryexplorer.model.UIRepositoryObjectRevisions;
import org.pentaho.di.ui.repository.repositoryexplorer.model.UIJob;
import org.pentaho.di.ui.repository.repositoryexplorer.model.UIRepositoryObject;
import org.pentaho.di.ui.repository.repositoryexplorer.model.UIRepositoryObjects;
import org.pentaho.di.ui.repository.repositoryexplorer.model.UITransformation;
import org.pentaho.ui.xul.XulEventSourceAdapter;

public class UIGit extends XulEventSourceAdapter {

  private Git git;
  private String path;
  private String authorName;
  private String commitMessage;

  public String findGitRepository( String pathname ) {
    Repository repository;
    try {
      repository = ( new FileRepositoryBuilder() ).readEnvironment() // scan environment GIT_* variables
          .findGitDir( new File( pathname ).getParentFile() ) // scan up the file system tree
          .build();
      return repository.getDirectory().getParent();
    } catch ( IOException e ) {
      return null;
    }
  }

  public Git getGit() {
    return git;
  }

  public void setGit( Git git ) {
    this.git = git;
  }

  public void setPath( String path ) {
    this.path = "".equals( path ) ? null : path;
    firePropertyChange( "path", null, path );
  }

  public String getPath() {
    return this.path;
  }

  public String getAuthorName() {
    return authorName;
  }

  public void setAuthorName( String authorName ) {
    this.authorName = authorName;
    firePropertyChange( "authorName", null, authorName );
  }

  public String getCommitMessage() {
    return commitMessage;
  }

  public void setCommitMessage( String commitMessage ) {
    this.commitMessage = commitMessage;
    firePropertyChange( "commitMessage", null, commitMessage );
  }

  public String getBranch() {
    try {
      return git.getRepository().getBranch();
    } catch ( Exception e ) {
      return "";
    }
  }

  public String getFullBranch() throws IOException {
    return git.getRepository().getFullBranch();
  }

  public String getRemote() {
    try {
      StoredConfig config = git.getRepository().getConfig();
      RemoteConfig remoteConfig = new RemoteConfig( config, Constants.DEFAULT_REMOTE_NAME );
      return remoteConfig.getURIs().iterator().next().toString();
    } catch ( Exception e ) {
      return "";
    }
  }

  public RemoteConfig addRemote( String s ) throws Exception {
    // Make sure you have only one URI for push
    removeRemote();

    URIish uri = new URIish( s );
    RemoteAddCommand cmd = git.remoteAdd();
    cmd.setName( Constants.DEFAULT_REMOTE_NAME );
    cmd.setUri( uri );
    firePropertyChange( "remote", null, s );
    return cmd.call();
  }

  public RemoteConfig removeRemote() throws GitAPIException {
    RemoteRemoveCommand cmd = git.remoteRemove();
    cmd.setName( Constants.DEFAULT_REMOTE_NAME );
    firePropertyChange( "remote", null, "" );
    return cmd.call();
  }

  public boolean hasRemote() throws IOException {
    StoredConfig config = git.getRepository().getConfig();
    Set<String> remotes = config.getSubsections( "remote" );
    return remotes.contains( Constants.DEFAULT_REMOTE_NAME );
  }

  public RevCommit commit( String name, String email, String message ) throws Exception {
    return git.commit().setAuthor( name, email ).setMessage( message ).call();
  }

  public UIRepositoryObjectRevisions getRevisionObjects() {
    UIRepositoryObjectRevisions revisions = new UIRepositoryObjectRevisions();
    try {
      Iterable<RevCommit> iterable = git.log().call();
      for ( RevCommit commit : iterable ) {
        PurObjectRevision rev = new PurObjectRevision(
          commit.getName().substring( 0, 7 ),
          commit.getAuthorIdent().getName(),
          commit.getAuthorIdent().getWhen(),
          commit.getShortMessage() );
        revisions.add( new UIRepositoryObjectRevision( (ObjectRevision) rev ) );
      }
    } catch ( Exception e ) {

    }
    return revisions;
  }

  public UIRepositoryObjects getUnstagedObjects() throws Exception {
    Set<String> files = new HashSet<String>();
    try {
      Status status = git.status().call();
      files.addAll( status.getModified() );
      files.addAll( status.getUntracked() );
    } catch ( Exception e ) {
    }
    return getObjects( files );
  }

  public UIRepositoryObjects getStagedObjects() throws Exception {
    Set<String> files = new HashSet<String>();
    try {
      Status status = git.status().call();
      files.addAll( status.getAdded() );
      files.addAll( status.getChanged() );
    } catch ( Exception e ) {
    }
    return getObjects( files );
  }

  public boolean hasStagedObjects() throws Exception {
    return getStagedObjects().size() != 0;
  }

  private UIRepositoryObjects getObjects( Set<String> files ) throws Exception {
    UIRepositoryObjects objs = new UIRepositoryObjects();
    for ( String file : files ) {
      UIRepositoryObject obj;
      Date date = new Date();
      ObjectId id = new StringObjectId( file );
      if ( file.endsWith( ".ktr" ) ) {
        RepositoryElementMetaInterface rc =  new RepositoryObject(
            id, file, null, "-", date, RepositoryObjectType.TRANSFORMATION, "", false );
        obj = new UITransformation( rc, null, null );
      } else if ( file.endsWith( ".kjb" ) ) {
        RepositoryElementMetaInterface rc =  new RepositoryObject(
            id, file, null, "-", date, RepositoryObjectType.JOB, "", false );
        obj = new UIJob( rc, null, null );
      } else {
        RepositoryElementMetaInterface rc =  new RepositoryObject(
            id, file, null, "-", date, RepositoryObjectType.UNKNOWN, "", false );
        obj = new UIJob( rc, null, null );
      }
      objs.add( obj );
    }
    return objs;
  }

  public void initGit( String baseDirectory ) throws IllegalStateException, GitAPIException {
    git = Git.init().setDirectory( new File( baseDirectory ) ).call();
    setPath( baseDirectory );
  }

  public void openGit( String baseDirectory ) throws IOException {
    git = Git.open( new File( baseDirectory ) );
    setPath( baseDirectory );
  }

  public void closeGit() {
    setPath( null );
    git.close();
    git = null;
  }

  /**
   * Equivalent of <tt>git fetch; git merge --ff</tt>
   *
   * @return PullResult
   * @throws Exception
   * @see <a href="http://www.kernel.org/pub/software/scm/git/docs/git-pull.html">Git documentation about Pull</a>
   */
  public PullResult pull() throws Exception {
    return git.pull().call();
  }

  public Ref resetHard() throws Exception {
    return git.reset().setMode( ResetType.HARD ).call();
  }

  public Iterable<PushResult> push() throws Exception {
    return git.push().call();
  }
}
