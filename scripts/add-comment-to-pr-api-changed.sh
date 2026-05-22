CHANGED=$(git diff --name-only "specs/pre-api.json" pre-api-stg.yaml)

if [ "$CHANGED" != "" ]; then
  const commentBody = `## :x: Change to API Spec detected
  This pull request updates the Open API specification.

  Monitor carefully when deploying to production, as sometimes the APIM fails at deployment stage.

  Follow the release process and manually check that the Streaming Manager is up after the release.

  See https://tools.hmcts.net/confluence/spaces/S28/pages/1958069495/API+release+process for details.`;

  // Find existing comment
  const { data: comments } = await github.rest.issues.listComments({
    owner: context.repo.owner,
    repo: context.repo.repo,
    issue_number: context.issue.number,
  });
  const botComment = comments.find(comment =>
    comment.user.type === 'Bot' &&
    comment.body.includes('Change to API Spec detected')
  );
  // If no existing comment, create new one
  if (!botComment) {
    await github.rest.issues.createComment({
      owner: context.repo.owner,
      repo: context.repo.repo,
      issue_number: context.issue.number,
      body: commentBody
    });
  }
fi
