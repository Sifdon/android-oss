package com.kickstarter.viewmodels;

import android.support.annotation.NonNull;
import android.util.Pair;

import com.kickstarter.R;
import com.kickstarter.libs.ActivityViewModel;
import com.kickstarter.libs.Environment;
import com.kickstarter.libs.KSCurrency;
import com.kickstarter.libs.KSString;
import com.kickstarter.libs.utils.BooleanUtils;
import com.kickstarter.libs.utils.I18nUtils;
import com.kickstarter.libs.utils.ListUtils;
import com.kickstarter.libs.utils.NumberUtils;
import com.kickstarter.libs.utils.ObjectUtils;
import com.kickstarter.libs.utils.PairUtils;
import com.kickstarter.libs.utils.ProgressBarUtils;
import com.kickstarter.libs.utils.ProjectUtils;
import com.kickstarter.libs.utils.SocialUtils;
import com.kickstarter.models.Category;
import com.kickstarter.models.Location;
import com.kickstarter.models.Photo;
import com.kickstarter.models.Project;
import com.kickstarter.ui.viewholders.ProjectViewHolder;

import org.joda.time.DateTime;

import java.math.RoundingMode;

import rx.Observable;
import rx.subjects.PublishSubject;

import static com.kickstarter.libs.rx.transformers.Transformers.combineLatestPair;
import static com.kickstarter.libs.rx.transformers.Transformers.ignoreValues;
import static com.kickstarter.libs.rx.transformers.Transformers.takeWhen;

public interface ProjectHolderViewModel {

  interface Inputs {
    void configureWith(Pair<Project, String> projectAndCountry);
    void projectSocialViewGroupClicked();
  }

  interface Outputs {
    Observable<String> avatarPhotoUrl();
    Observable<String> backersCountTextViewText();
    Observable<Boolean> backingViewGroupIsGone();
    Observable<String> blurbTextViewText();
    Observable<String> categoryTextViewText();
    Observable<String> commentsCountTextViewText();
    Observable<String> creatorNameTextViewText();
    Observable<String> deadlineCountdownTextViewText();
    Observable<String> featuredTextViewRootCategory();
    Observable<Boolean> featuredViewGroupIsGone();
    Observable<String> goalStringForTextView();
    Observable<String> locationTextViewText();
    Observable<Integer> percentageFundedProgress();
    Observable<Boolean> percentageFundedProgressBarIsGone();
    Observable<Boolean> playButtonIsGone();
    Observable<String> pledgedTextViewText();
    Observable<Boolean> potdViewGroupIsGone();
    Observable<DateTime> projectDisclaimerGoalReachedDateTime();
    Observable<Pair<String, DateTime>> projectDisclaimerGoalNotReachedString();
    Observable<Boolean> projectDisclaimerTextViewIsGone();
    Observable<Project> projectForDeadlineCountdownTextView();
    Observable<Integer> projectMetadataViewGroupBackgroundDrawableInt();
    Observable<Boolean> projectMetadataViewGroupIsGone();
    Observable<String> projectNameTextViewText();
    Observable<Photo> projectPhoto();
    Observable<Boolean> projectSocialImageViewIsGone();
    Observable<String> projectSocialImageViewUrl();
    Observable<String> projectSocialTextViewText();
    Observable<Boolean> projectSocialViewGroupIsGone();
    Observable<Integer> projectStateViewGroupBackgroundColorInt();
    Observable<Boolean> projectStateViewGroupIsGone();
    Observable<Boolean> shouldSetDefaultStatsMargins();
    Observable<Void> setCanceledProjectStateView();
    Observable<Void> setProjectSocialClick();
    Observable<DateTime> setSuccessfulProjectStateView();
    Observable<Void> setSuspendedProjectStateView();
    Observable<DateTime> setUnsuccessfulProjectStateView();
    Observable<Project> startProjectSocialActivity();
    Observable<String> updatesCountTextViewText();
    Observable<Pair<String, String>> usdConversionGoalAndPledgedText();
    Observable<Boolean> usdConversionTextViewIsGone();
  }

  final class ViewModel extends ActivityViewModel<ProjectViewHolder> implements Inputs, Outputs {
    private final KSCurrency ksCurrency;
    private final KSString ksString;

    public ViewModel(final @NonNull Environment environment) {
      super(environment);
      this.ksCurrency = environment.ksCurrency();
      this.ksString = environment.ksString();

      final Observable<Project> project = this.projectAndCountry.map(PairUtils::first);
      final Observable<String> country = this.projectAndCountry.map(PairUtils::second);
      final Observable<ProjectUtils.Metadata> projectMetadata = project.map(ProjectUtils::metadataForProject);

      this.avatarPhotoUrl = project.map(p -> p.creator().avatar().medium());
      this.backersCountTextViewText = project.map(Project::backersCount).map(NumberUtils::format);

      this.backingViewGroupIsGone = projectMetadata
        .map(ProjectUtils.Metadata.BACKING::equals)
        .map(BooleanUtils::negate);

      this.blurbTextViewText = project.map(Project::blurb);
      this.categoryTextViewText = project.map(Project::category).filter(ObjectUtils::isNotNull).map(Category::name);
      this.commentsCountTextViewText = project.map(Project::commentsCount).filter(ObjectUtils::isNotNull).map(NumberUtils::format);
      this.creatorNameTextViewText = project.map(p -> p.creator().name());
      this.deadlineCountdownTextViewText = project.map(ProjectUtils::deadlineCountdownValue).map(NumberUtils::format);

      this.featuredViewGroupIsGone = projectMetadata
        .map(m -> m == ProjectUtils.Metadata.BACKING
          || m == ProjectUtils.Metadata.POTD
          || m != ProjectUtils.Metadata.CATEGORY_FEATURED
        );

      this.featuredTextViewRootCategory = this.featuredViewGroupIsGone
        .filter(BooleanUtils::isFalse)
        .compose(combineLatestPair(project))
        .map(bp -> bp.second.category())
        .filter(ObjectUtils::isNotNull)
        .map(Category::root)
        .filter(ObjectUtils::isNotNull)
        .map(Category::name);

      this.goalStringForTextView = project
        .map(p -> this.ksCurrency.format(p.goal(), p, false, true, RoundingMode.DOWN));

      this.locationTextViewText = project.map(Project::location).filter(ObjectUtils::isNotNull).map(Location::displayableName);
      this.percentageFundedProgress = project.map(Project::percentageFunded).map(ProgressBarUtils::progress);

      // todo: simplify to if project is not live?
      this.percentageFundedProgressBarIsGone = project
        .map(p -> p.isSuccessful() || p.isCanceled() || p.isFailed() || p.isSuspended());

      this.playButtonIsGone = project.map(Project::hasVideo).map(BooleanUtils::negate);

      this.pledgedTextViewText = project
        .map(p -> this.ksCurrency.format(p.pledged(), p, false, true, RoundingMode.DOWN));

      this.potdViewGroupIsGone = projectMetadata
        .map(m -> m == ProjectUtils.Metadata.BACKING || m != ProjectUtils.Metadata.POTD);

      this.projectDisclaimerGoalReachedDateTime = project
        .filter(Project::isFunded)
        .map(Project::deadline);

      this.projectDisclaimerGoalNotReachedString = project
        .filter(p -> p.deadline() != null && p.isLive() && !p.isFunded())
        .map(p -> Pair.create(this.ksCurrency.format(p.goal(), p, true), p.deadline()));

      this.projectDisclaimerTextViewIsGone = project.map(p -> p.deadline() == null || !p.isLive());

      this.projectForDeadlineCountdownTextView = project;

      this.projectMetadataViewGroupBackgroundDrawableInt = projectMetadata
        .filter(ProjectUtils.Metadata.BACKING::equals)
        .map(__ -> R.drawable.rect_green_grey_stroke);

      // todo: is this a legit operator
      this.projectMetadataViewGroupIsGone = projectMetadata.isEmpty();

      this.projectNameTextViewText = project.map(Project::name);
      this.projectPhoto = project.map(Project::photo);

      this.projectSocialImageViewUrl = project
        .map(Project::friends)
        .filter(ObjectUtils::isNotNull)
        .map(ListUtils::first)
        .map(f -> f.avatar().small());

      this.projectSocialTextViewText = project
        .map(Project::friends)
        .filter(ObjectUtils::isNotNull)
        .map(f -> SocialUtils.projectCardFriendNamepile(f, this.ksString));

      this.projectSocialViewGroupIsGone = project.map(Project::isFriendBacking).map(BooleanUtils::negate);

      this.projectStateViewGroupBackgroundColorInt = project
        .map(p -> p.state().equals(Project.STATE_SUCCESSFUL) ? R.color.green_alpha_50 : R.color.ksr_grey_400);

      // todo: negate percentageFundedProgressBarIsGone?
      this.projectStateViewGroupIsGone = project
        .map(p -> !p.isSuccessful() || !p.isCanceled() || !p.isFailed() || !p.isSuspended());

      this.projectSocialImageViewIsGone = this.projectSocialViewGroupIsGone;
      this.shouldSetDefaultStatsMargins = this.projectSocialViewGroupIsGone;
      this.setCanceledProjectStateView = project.filter(Project::isCanceled).compose(ignoreValues());
      this.setProjectSocialClick = project.map(Project::friends).map(fs -> fs.size() > 2).compose(ignoreValues());

      this.setSuccessfulProjectStateView = project
        .filter(Project::isSuccessful)
        .map(p -> ObjectUtils.coalesce(p.stateChangedAt(), new DateTime()));

      this.setSuspendedProjectStateView = project.filter(Project::isSuspended).compose(ignoreValues());

      this.setUnsuccessfulProjectStateView = project
        .filter(Project::isFailed)
        .map(p -> ObjectUtils.coalesce(p.stateChangedAt(), new DateTime()));

      this.startProjectSocialActivity = project.compose(takeWhen(this.projectSocialViewGroupClicked));

      this.updatesCountTextViewText = project
        .map(Project::updatesCount)
        .filter(ObjectUtils::isNotNull)
        .map(NumberUtils::format);

      this.usdConversionTextViewIsGone = this.projectAndCountry
        .map(pc -> I18nUtils.isCountryUS(pc.second) && !I18nUtils.isCountryUS(pc.first.country()))
        .map(BooleanUtils::negate);

      this.usdConversionGoalAndPledgedText = project
        .map(p -> {
          final String goal = this.ksCurrency.format(p.pledged(), p);
          final String pledged = this.ksCurrency.format(p.goal(), p);
          return Pair.create(goal, pledged);
        });
    }

    private final PublishSubject<Pair<Project, String>> projectAndCountry = PublishSubject.create();
    private final PublishSubject<Void> projectSocialViewGroupClicked = PublishSubject.create();

    private final Observable<String> avatarPhotoUrl;
    private final Observable<String> backersCountTextViewText;
    private final Observable<Boolean> backingViewGroupIsGone;
    private final Observable<String> blurbTextViewText;
    private final Observable<String> categoryTextViewText;
    private final Observable<String> commentsCountTextViewText;
    private final Observable<String> creatorNameTextViewText;
    private final Observable<String> deadlineCountdownTextViewText;
    private final Observable<String> featuredTextViewRootCategory;
    private final Observable<Boolean> featuredViewGroupIsGone;
    private final Observable<String> goalStringForTextView;
    private final Observable<String> locationTextViewText;
    private final Observable<Integer> percentageFundedProgress;
    private final Observable<Boolean> percentageFundedProgressBarIsGone;
    private final Observable<Boolean> playButtonIsGone;
    private final Observable<String> pledgedTextViewText;
    private final Observable<Boolean> potdViewGroupIsGone;
    private final Observable<DateTime> projectDisclaimerGoalReachedDateTime;
    private final Observable<Pair<String, DateTime>> projectDisclaimerGoalNotReachedString;
    private final Observable<Boolean> projectDisclaimerTextViewIsGone;
    private final Observable<Project> projectForDeadlineCountdownTextView;
    private final Observable<Integer> projectMetadataViewGroupBackgroundDrawableInt;
    private final Observable<Boolean> projectMetadataViewGroupIsGone;
    private final Observable<String> projectNameTextViewText;
    private final Observable<Photo> projectPhoto;
    private final Observable<Boolean> projectSocialImageViewIsGone;
    private final Observable<String> projectSocialImageViewUrl;
    private final Observable<String> projectSocialTextViewText;
    private final Observable<Boolean> projectSocialViewGroupIsGone;
    private final Observable<Integer> projectStateViewGroupBackgroundColorInt;
    private final Observable<Boolean> projectStateViewGroupIsGone;
    private final Observable<Void> setCanceledProjectStateView;
    private final Observable<Void> setProjectSocialClick;
    private final Observable<DateTime> setSuccessfulProjectStateView;
    private final Observable<Void> setSuspendedProjectStateView;
    private final Observable<DateTime> setUnsuccessfulProjectStateView;
    private final Observable<Project> startProjectSocialActivity;
    private final Observable<Boolean> shouldSetDefaultStatsMargins;
    private final Observable<String> updatesCountTextViewText;
    private final Observable<Boolean> usdConversionTextViewIsGone;
    private final Observable<Pair<String, String>> usdConversionGoalAndPledgedText;

    public final Inputs inputs = this;
    public final Outputs outputs = this;

    @Override public void configureWith(final @NonNull Pair<Project, String> projectAndCountry) {
      this.projectAndCountry.onNext(projectAndCountry);
    }
    @Override public void projectSocialViewGroupClicked() {
      this.projectSocialViewGroupClicked.onNext(null);
    }

    @Override public @NonNull Observable<String> avatarPhotoUrl() {
      return this.avatarPhotoUrl;
    }
    @Override public @NonNull Observable<Boolean> backingViewGroupIsGone() {
      return this.backingViewGroupIsGone;
    }
    @Override public @NonNull Observable<String> backersCountTextViewText() {
      return this.backersCountTextViewText;
    }
    @Override public @NonNull Observable<String> blurbTextViewText() {
      return this.blurbTextViewText;
    }
    @Override public @NonNull Observable<String> categoryTextViewText() {
      return this.categoryTextViewText;
    }
    @Override public @NonNull Observable<String> commentsCountTextViewText() {
      return this.commentsCountTextViewText;
    }
    @Override public @NonNull Observable<String> creatorNameTextViewText() {
      return this.creatorNameTextViewText;
    }
    @Override public @NonNull Observable<String> deadlineCountdownTextViewText() {
      return this.deadlineCountdownTextViewText;
    }
    @Override public @NonNull Observable<String> featuredTextViewRootCategory() {
      return this.featuredTextViewRootCategory;
    }
    @Override public @NonNull Observable<Boolean> featuredViewGroupIsGone() {
      return this.featuredViewGroupIsGone;
    }
    @Override public @NonNull Observable<String> goalStringForTextView() {
      return this.goalStringForTextView;
    }
    @Override public @NonNull Observable<String> locationTextViewText() {
      return this.locationTextViewText;
    }
    @Override public @NonNull Observable<Integer> percentageFundedProgress() {
      return this.percentageFundedProgress;
    }
    @Override public @NonNull Observable<Boolean> percentageFundedProgressBarIsGone() {
      return this.percentageFundedProgressBarIsGone;
    }
    @Override public @NonNull Observable<Boolean> playButtonIsGone() {
      return this.playButtonIsGone;
    }
    @Override public @NonNull Observable<String> pledgedTextViewText() {
      return this.pledgedTextViewText;
    }
    @Override public @NonNull Observable<Boolean> potdViewGroupIsGone() {
      return this.potdViewGroupIsGone;
    }
    @Override public @NonNull Observable<DateTime> projectDisclaimerGoalReachedDateTime() {
      return this.projectDisclaimerGoalReachedDateTime;
    }
    @Override public @NonNull Observable<Pair<String, DateTime>> projectDisclaimerGoalNotReachedString() {
      return this.projectDisclaimerGoalNotReachedString;
    }
    @Override public @NonNull Observable<Boolean> projectDisclaimerTextViewIsGone() {
      return this.projectDisclaimerTextViewIsGone;
    }
    @Override public @NonNull Observable<Project> projectForDeadlineCountdownTextView() {
      return this.projectForDeadlineCountdownTextView;
    }
    @Override public @NonNull Observable<Integer> projectMetadataViewGroupBackgroundDrawableInt() {
      return this.projectMetadataViewGroupBackgroundDrawableInt;
    }
    @Override public @NonNull Observable<Boolean> projectMetadataViewGroupIsGone() {
      return this.projectMetadataViewGroupIsGone;
    }
    @Override public @NonNull Observable<String> projectNameTextViewText() {
      return this.projectNameTextViewText;
    }
    @Override public @NonNull Observable<String> projectSocialTextViewText() {
      return this.projectSocialTextViewText;
    }
    @Override public @NonNull Observable<Photo> projectPhoto() {
      return this.projectPhoto;
    }
    @Override public @NonNull Observable<Boolean> projectSocialImageViewIsGone() {
      return this.projectSocialImageViewIsGone;
    }
    @Override public @NonNull Observable<String> projectSocialImageViewUrl() {
      return this.projectSocialImageViewUrl;
    }
    @Override public @NonNull Observable<Boolean> projectSocialViewGroupIsGone() {
      return this.projectSocialViewGroupIsGone;
    }
    @Override public @NonNull Observable<Integer> projectStateViewGroupBackgroundColorInt() {
      return this.projectStateViewGroupBackgroundColorInt;
    }
    @Override public @NonNull Observable<Boolean> projectStateViewGroupIsGone() {
      return this.projectStateViewGroupIsGone;
    }
    @Override public @NonNull Observable<Project> startProjectSocialActivity() {
      return this.startProjectSocialActivity;
    }
    @Override public @NonNull Observable<Void> setCanceledProjectStateView() {
      return this.setCanceledProjectStateView;
    }
    @Override public @NonNull Observable<Void> setProjectSocialClick() {
      return this.setProjectSocialClick;
    }
    @Override public @NonNull Observable<DateTime> setSuccessfulProjectStateView() {
      return this.setSuccessfulProjectStateView;
    }
    @Override public @NonNull Observable<Void> setSuspendedProjectStateView() {
      return this.setSuspendedProjectStateView;
    }
    @Override public @NonNull Observable<DateTime> setUnsuccessfulProjectStateView() {
      return this.setUnsuccessfulProjectStateView;
    }
    @Override public @NonNull Observable<Boolean> shouldSetDefaultStatsMargins() {
      return this.shouldSetDefaultStatsMargins;
    }
    @Override public @NonNull Observable<String> updatesCountTextViewText() {
      return this.updatesCountTextViewText;
    }
    @Override public @NonNull Observable<Boolean> usdConversionTextViewIsGone() {
      return this.usdConversionTextViewIsGone;
    }
    @Override public @NonNull Observable<Pair<String, String>> usdConversionGoalAndPledgedText() {
      return this.usdConversionGoalAndPledgedText;
    }
  }
}