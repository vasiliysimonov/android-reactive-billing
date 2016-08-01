package synergy.android.billing.reactive;

import android.app.Activity;
import android.content.Intent;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import rx.Observable;
import rx.subjects.BehaviorSubject;
import synergy.android.billing.v3.util.IabException;
import synergy.android.billing.v3.util.IabHelper;
import synergy.android.billing.v3.util.IabResult;
import synergy.android.billing.v3.util.Inventory;

public class Billing {

	private static final int REQUEST_PURCHASE = 12063;

	private final BehaviorSubject<Inventory> inventorySubject = BehaviorSubject.create();
	private final IabHelper iabHelper;
	private final ExecutorService background;
	private Future<?> lastInventoryJob;
	private boolean initialized;

	public Billing(IabHelper iabHelper, ExecutorService backgroundExecutor) {
		this.iabHelper = iabHelper;
		this.background = backgroundExecutor;
		iabHelper.startSetup(this::onSetupFinished);
	}

	public boolean isAvailable() {
		return iabHelper.isBillingAvailable();
	}

	public Observable<Inventory> inventoryObservable() {
		return inventorySubject.doOnSubscribe(this::requestInventory);
	}

	@Nullable
	public Inventory getLatestInventory() {
		return inventorySubject.getValue();
	}

	/* TODO: for security, generate your payload here for verification. See the comments on
     *        verifyDeveloperPayload() for more info. Since this is a SAMPLE, we just use
     *        an empty string, but on a production app you should carefully generate this. */
	public void startSubscription(Activity activity, String item) {
		startSubscription(activity, item, "");
	}

	public void startSubscription(Activity activity, String item, String payload) {
		if (!iabHelper.isPurchaseFlowLaunched()) {
			iabHelper.launchPurchaseFlow(activity, item, IabHelper.ITEM_TYPE_SUBS, REQUEST_PURCHASE, onPurchaseFinishedListener, payload);
		}
	}

	public void startPurchase(Activity activity, String item) {
		startPurchase(activity, item, "");
	}

	public void startPurchase(Activity activity, String item, String payload) {
		if (!iabHelper.isPurchaseFlowLaunched()) {
			iabHelper.launchPurchaseFlow(activity, item, IabHelper.ITEM_TYPE_INAPP, REQUEST_PURCHASE, onPurchaseFinishedListener, payload);
		}
	}

	public boolean handleResult(int requestCode, int resultCode, Intent data) {
		return iabHelper.handleActivityResult(requestCode, resultCode, data);
	}

	public void requestInventory() {
		if (initialized && (lastInventoryJob == null || lastInventoryJob.isDone())) {
			lastInventoryJob = background.submit(() -> {
				try {
					Inventory i = iabHelper.queryInventory(false, null);
					inventorySubject.onNext(i);
				} catch (Exception e) {
					inventorySubject.onError(e);
				}
			});
		}
	}

	@VisibleForTesting
	void onSetupFinished(IabResult result) {
		if (result.isSuccess()) {
			initialized = true;
			requestInventory();
		} else {
			inventorySubject.onError(new IabException(result.getResponse(), result.getMessage()));
		}
	}

	private final IabHelper.OnIabPurchaseFinishedListener onPurchaseFinishedListener = (result, info) -> {
		if (result.isSuccess()) {
			requestInventory();
		}
	};
}
