package synergy.android.billing.reactive;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import rx.Observable;
import rx.subjects.BehaviorSubject;
import synergy.android.billing.v3.util.IabException;
import synergy.android.billing.v3.util.IabHelper;
import synergy.android.billing.v3.util.IabResult;
import synergy.android.billing.v3.util.Inventory;
import synergy.android.billing.v3.util.Purchase;

public class Billing {

	private static final int REQUEST_PURCHASE = 12063;

	private final BehaviorSubject<Inventory> inventorySubject = BehaviorSubject.create();
	private final IabHelper iabHelper;
	private final ExecutorService background;
	private Future<?> lastInventoryJob;
	private boolean initialized;

	public Billing(Context context, String apiKey) {
		this(context, apiKey, Executors.newSingleThreadExecutor());
	}

	public Billing(Context context, String apiKey, ExecutorService backgroundExecutor) {
		this(new IabHelper(context, apiKey), backgroundExecutor);
	}

	@VisibleForTesting
	Billing(IabHelper iabHelper, ExecutorService backgroundExecutor) {
		this.iabHelper = iabHelper;
		this.background = backgroundExecutor;
		iabHelper.startSetup(this::onSetupFinished);
	}

	public boolean isAvailable() {
		return iabHelper.isBillingAvailable();
	}

	public boolean isSubscriptionSupported() {
		return iabHelper.subscriptionsSupported();
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
		try {
			iabHelper.launchPurchaseFlow(activity, item, IabHelper.ITEM_TYPE_SUBS, null, REQUEST_PURCHASE, onPurchaseFinishedListener, payload);
		} catch (IabHelper.IabAsyncInProgressException e) {
			throw new IllegalStateException(e); // programmer's error
		}
	}

	public void startPurchase(Activity activity, String item) {
		startPurchase(activity, item, "");
	}

	public void startPurchase(Activity activity, String item, String payload) {
		try {
			iabHelper.launchPurchaseFlow(activity, item, IabHelper.ITEM_TYPE_INAPP, null, REQUEST_PURCHASE, onPurchaseFinishedListener, payload);
		} catch (IabHelper.IabAsyncInProgressException e) {
			throw new IllegalStateException(e); // programmer's error
		}
	}

	public boolean handleResult(int requestCode, int resultCode, Intent data) {
		return iabHelper.handleActivityResult(requestCode, resultCode, data);
	}

	public Future<?> consume(Purchase item) {
		return background.submit(() -> {
			try {
				iabHelper.consume(item);
				requestInventory();
			} catch (IabException e) {
				throw new IllegalStateException(e); // programmer's error
			}
		});
	}

	public void requestInventory() {
		if (initialized && (lastInventoryJob == null || lastInventoryJob.isDone())) {
			lastInventoryJob = background.submit(() -> {
				try {
					Inventory i = iabHelper.queryInventory();
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
