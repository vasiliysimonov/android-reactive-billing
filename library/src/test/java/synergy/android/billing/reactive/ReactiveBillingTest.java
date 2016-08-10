package synergy.android.billing.reactive;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import rx.functions.Action1;
import synergy.android.billing.v3.util.IabHelper;
import synergy.android.billing.v3.util.IabResult;
import synergy.android.billing.v3.util.Inventory;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.notNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class ReactiveBillingTest {

	@Mock
	IabHelper helper;

	private ListeningExecutorService sameThreadExecutor = MoreExecutors.newDirectExecutorService();

	@Test
	public void when_create_then_startBillingInitialization() throws Exception {
		new Billing(helper, sameThreadExecutor);

		verify(helper).startSetup(notNull(IabHelper.OnIabSetupFinishedListener.class));
	}

	@Test
	public void when_setupSucceeded_queryInventory() throws Exception {
		Billing wrapper = new Billing(helper, sameThreadExecutor);
		wrapper.onSetupFinished(new IabResult(IabHelper.BILLING_RESPONSE_RESULT_OK, null));

		verify(helper).queryInventory();
	}

	@Test
	public void when_setupFailed_neverQueryInventory() throws Exception {
		Billing wrapper = new Billing(helper, sameThreadExecutor);
		wrapper.onSetupFinished(new IabResult(IabHelper.BILLING_RESPONSE_RESULT_ERROR, null));
		wrapper.inventoryObservable().doOnError(e -> { });

		verify(helper, never()).queryInventory();
	}

	@Test
	public void when_notSetup_doNotQueryInventory() throws Exception {
		Billing wrapper = new Billing(helper, sameThreadExecutor);
		wrapper.inventoryObservable().subscribe();

		verify(helper, never()).queryInventory();
	}

	@Test
	public void when_subscribedBeforeSetup_receiveInventory() throws Exception {
		Billing wrapper = new Billing(helper, sameThreadExecutor);
		Action1<Inventory> mockSubscription = mock(Action1.class);
		wrapper.inventoryObservable().subscribe(mockSubscription);
		wrapper.onSetupFinished(new IabResult(IabHelper.BILLING_RESPONSE_RESULT_OK, null));

		verify(mockSubscription).call(any());
	}

}