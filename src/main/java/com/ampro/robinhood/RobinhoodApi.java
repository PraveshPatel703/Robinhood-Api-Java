package com.ampro.robinhood;

import com.ampro.robinhood.endpoint.ApiElement;
import com.ampro.robinhood.endpoint.ApiElementList;
import com.ampro.robinhood.endpoint.account.data.*;
import com.ampro.robinhood.endpoint.account.methods.*;
import com.ampro.robinhood.endpoint.authorize.data.Token;
import com.ampro.robinhood.endpoint.authorize.methods.AuthorizeWithoutMultifactor;
import com.ampro.robinhood.endpoint.authorize.methods.LogoutFromRobinhood;
import com.ampro.robinhood.endpoint.collection.data.InstrumentCollectionList;
import com.ampro.robinhood.endpoint.collection.methods.GetCollectionData;
import com.ampro.robinhood.endpoint.fundamentals.data.TickerFundamentalElement;
import com.ampro.robinhood.endpoint.fundamentals.data.TickerFundimentalElementList;
import com.ampro.robinhood.endpoint.fundamentals.methods.GetTickerFundamental;
import com.ampro.robinhood.endpoint.fundamentals.methods.GetTickerFundamentalList;
import com.ampro.robinhood.endpoint.instrument.data.InstrumentElement;
import com.ampro.robinhood.endpoint.instrument.data.InstrumentElementList;
import com.ampro.robinhood.endpoint.instrument.methods.GetAllInstruments;
import com.ampro.robinhood.endpoint.instrument.methods.GetInstrumentByTicker;
import com.ampro.robinhood.endpoint.instrument.methods.SearchInstrumentsByKeyword;
import com.ampro.robinhood.endpoint.option.data.Option;
import com.ampro.robinhood.endpoint.option.data.OptionElementList;
import com.ampro.robinhood.endpoint.option.methods.GetOptionsMethod;
import com.ampro.robinhood.endpoint.orders.data.SecurityOrderElement;
import com.ampro.robinhood.endpoint.orders.data.SecurityOrderElementList;
import com.ampro.robinhood.endpoint.orders.enums.OrderTransactionType;
import com.ampro.robinhood.endpoint.orders.enums.TimeInForce;
import com.ampro.robinhood.endpoint.orders.methods.*;
import com.ampro.robinhood.endpoint.quote.data.TickerQuoteElement;
import com.ampro.robinhood.endpoint.quote.data.TickerQuoteElementList;
import com.ampro.robinhood.endpoint.quote.methods.GetTickerQuote;
import com.ampro.robinhood.endpoint.quote.methods.GetTickerQuoteList;
import com.ampro.robinhood.net.ApiMethod;
import com.ampro.robinhood.net.pagination.PaginatedIterator;
import com.ampro.robinhood.net.request.RequestManager;
import com.ampro.robinhood.net.request.RequestStatus;
import com.ampro.robinhood.throwables.NotLoggedInException;
import com.ampro.robinhood.throwables.RequestTooLargeException;
import com.ampro.robinhood.throwables.RobinhoodApiException;
import com.ampro.robinhood.throwables.TickerNotFoundException;
import io.github.openunirest.http.exceptions.UnirestException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.ampro.robinhood.net.request.RequestStatus.FAILURE;
import static com.ampro.robinhood.net.request.RequestStatus.SUCCESS;

/**
 * <p><h2>
 * A {@link RobinhoodApi} instance is used as an intermediary between the
 * Robinhood servers and your java/kotlin/whatever application.
 * </h2>
 * <p>
 *     <h3>{@link Configuration}</h3>
 *     Each API instance contains it's own {@link Configuration} which contains
 *     information about account tokens and urls (if the API has been logged
 *     in).
 *
 * @author Conrad Weisse, Jonathan Augustine
 */
public class RobinhoodApi {

	/** The Logger object used for the custom error handling */
	public static final Logger log = Logger.getLogger(RobinhoodApi.class.getName());

	/** The instance used to make the requests (singleton) */
	private static RequestManager requestManager;

	/**
	 * The active instance of the Configuration Manager.
     * The Auth-token is stored in this instance.
	 */
	private final Configuration config;

	/**
	 * Constructor which creates all of the access points to use the API.
	 * This constructor does not require the Username and Password, thus giving limited
	 * access to the API. See Robinhood Unofficial Documentation at following link
	 * to see what can and cannot be used if you do not authorize a user
	 */
	public RobinhoodApi() {
		//Do nothing. Allow users to access the unauthorized sections of the API
		RobinhoodApi.requestManager = RequestManager.getInstance();
		this.config = new Configuration();
	}

	/**
	 * Constructor which creates all of the access points to use the API.
	 * This constructor requires both a Username and Password and attempts to authorize
	 * the user. On success, the Authorization Token will be stored in the
	 * ConfigurationManager instance to be retrieved elsewhere.
	 * On failure, an error will be thrown.
	 * @param username The user's email (that they use with robinhood)
	 * @param password The user's password
	 * @throws NotLoggedInException If the login failed
	 */
	public RobinhoodApi(String username, String password)
    throws RobinhoodApiException {

		//Construct the manager
		RobinhoodApi.requestManager = RequestManager.getInstance();
        this.config = new Configuration();

		//Log the user in and store the auth token
        RequestStatus status = logUserIn(username, password);
        if (status == FAILURE) {
            throw new RobinhoodApiException("Failed to log user in: " + status.getValue());
        }
	}

	/**
	 * Method which returns the authentication for the logged in user, if one exists.
	 * <br> To check whether an API instance is logged in, use
	 * {@link RobinhoodApi#isLoggedIn()}.
	 * @throws NotLoggedInException if not login information is available.
	 */
	public String getAccountAuthToken() {
		return this.config.getToken();
	}

	/**
	 * Method allowing a user to input a token without logging in.
	 * It is not suggested you use this unless you have a specific reason where
	 * you need to inject a auth token into the instance, generally allowing the
	 * system to resolve this token with a username and password is more 'secure'.
	 */
	@Deprecated
	public void setAuthToken(String token) {
		this.config.setAuthToken(token);
	}

	/**
	 * Method which logs a user in given a email and password. This method
	 * automatically stores the authorization token in with the instance,
	 * allowing any method which requires the token to have immediate access to
	 * it.
	 *
	 * This method is ran if you created the RobinhoodApi class using the
	 * constructor with both a email and password, but is available if you
	 * wish to get the authorization token again. Usually ran after the user
	 * is logged out to refresh the token
	 *
	 * @param email The user's email
	 * @param password The user's password
	 * @return {@link RequestStatus#FAILURE} if the user could not be logged in.
	 *                  {@link RequestStatus#SUCCESS} otherwise
	 *
	 */
	public RequestStatus logUserIn(String email, String password) {
        //TODO: Implement multifactor authorization
        try {
            //Save the token into the configuration to be used with other methods
            ApiMethod method = new AuthorizeWithoutMultifactor(email, password);
            Token token = requestManager.makeApiRequest(method);
            if (token.getToken()== null) {
                return FAILURE.setValue("no token");
            }

            this.config.setAuthToken(token.getToken());

            //Save the account number into the config to be used with other methods
            ApiMethod accountMethod = new GetAccounts(this.config);

            //TODO: Clean up the following line, it should not have to use
            //the array wrapper. Tuck that code elsewhere
            AccountArrayWrapper requestData = requestManager.makeApiRequest(accountMethod);
            if (requestData.getResult() == null) {
                return FAILURE.setValue("no account wrapper");
            }
            AccountElement data = requestData.getResult();

            //If there is no account number, something went wrong.
            if (data.getAccountNumber() == null) {
            	RobinhoodApi.log.log(Level.SEVERE, "Failed to get account Number.");
            	RobinhoodApi.log.log(Level.SEVERE, "Unable to login!");
            	return FAILURE.setValue("Failed to get account Number.");
            }

            this.config.setAccountNumber(data.getAccountNumber());

        } catch (UnirestException | NullPointerException e) {
            RobinhoodApi.log.throwing(RobinhoodApi.class.getName(), "logUserIn", e);
            return FAILURE;
        }

        return SUCCESS;
    }

	/**
	 * Method which forces the authorization token to expire, logging the user
	 * out if the user is currently logged in. You should never see a
	 * "FAILURE" response from this. If so, file a bug report on github
	 * @return an enum containing either "SUCCESS", "FAILURE" or "NOT_LOGGED_IN"
     * @throws RobinhoodApiException
	 */
	public RequestStatus logUserOut() {
		if(!this.isLoggedIn()) {
			return RequestStatus.NOT_LOGGED_IN;
		}

		//Create the APIMethod which attempts to log the user out, and run it
		ApiMethod method = new LogoutFromRobinhood(this.config);
		method.addAuthTokenParameter();
	    requestManager.makeApiRequest(method); //Logout has no return

        //Just to be safe, wipe the token from memory
        this.config.clear();

		//If we made it to this point without throwing something, it worked!
		return SUCCESS;
	}

	//ACCOUNT DATA

	/**
	 * Method returning a {@link AccountElement} using the currently logged in
     * user
     * @return The requested {@link AccountElement}
	 * @throws NotLoggedInException if the user is not logged in
	 */
	public AccountElement getAccountData() {

		//Create the API method for this request
		ApiMethod method = new GetAccounts(this.config);

		//TODO: This is a temporary fix, as the Robinhood API seems
		//to have some features implemented, but are not used yet
		AccountArrayWrapper data = requestManager.makeApiRequest(method);
		return data.getResult();
	}

	/**
	 * Method returning a {@link BasicUserInfoElement} for the currently logged in user
	 * @throws NotLoggedInException if the user is not logged in
	 * @throws RobinhoodApiException
	 */
	public BasicUserInfoElement getBasicUserInfo()  {
		//Create the API method for the request
		ApiMethod method = new GetBasicUserInfo(this.config);
		return requestManager.makeApiRequest(method);
	}

	/**
	 * Method returning a {@link BasicAccountHolderInfoElement} for the currently logged in user
	 * @throws NotLoggedInException if the user is not logged in
	 */
	public BasicAccountHolderInfoElement getAccountHolderInfo() {
		//Create the API method
		ApiMethod method = new GetBasicAccountHolderInfo(this.config);
		return requestManager.makeApiRequest(method);
	}

	/**
	 * Method returning a {@link AccountHolderAffiliationElement} for the currently logged in user
	 * @throws NotLoggedInException if the user is not logged in
	 */
	public AccountHolderAffiliationElement getAccountHolderAffiliation()
    throws RobinhoodApiException {
		//Create the API method
		ApiMethod method = new GetAccountHolderAffiliationInfo(this.config);
		return requestManager.makeApiRequest(method);
	}

	/**
	 * Method returning a {@link AccountHolderEmploymentElement} for the currently logged in user
	 * @throws NotLoggedInException if the user is not logged in
	 */
	public AccountHolderEmploymentElement getAccountHolderEmployment()
    throws RobinhoodApiException {
		//Create the API method
		ApiMethod method = new GetAccountHolderEmploymentInfo(this.config);
		return requestManager.makeApiRequest(method);
	}

	/**
	 * Method returning a {@link AccountHolderInvestmentProfile} for the
     * currently logged in user
     * @return AccountHolderInvestmentProfile
	 * @throws NotLoggedInException if the user is not logged in
	 */
	public AccountHolderInvestmentProfile getAccountInvestmentProfile()
    throws RobinhoodApiException {
		//Create the API method
		ApiMethod method = new GetAccountHolderInvestmentProfile(this.config);
		return requestManager.makeApiRequest(method);
	}

	//ORDERS

    /**
     * Returns a list of {@link PositionElement} for each entry on the account's
     * watchlist. If the quantity of the {@link PositionElement} is above 0,
     * that means that you have an active position in that stock. All of the
     * other information which can be retrieved from this can be found in the
     * PositionElement page itself
     * @return
     * @throws RobinhoodApiException
     * @throws NotLoggedInException
     */
    public List<PositionElement> getAccountWatchlist()
    throws RobinhoodApiException {
        //Create the API method
        ApiMethod method = new GetAccountPositions(this.config);
        method.addAuthTokenParameter();
        //Return the current account positions
        PositionElementList response = requestManager.makeApiRequest(method);
        return response.getResults();

    }

    /**
     * Method which gets all of the account positions a user actually has shares in.
     * @return {@link PositionElement} containing all of the stocks an account has shares in
     * @throws RobinhoodApiException
     * @throws NotLoggedInException
     */
    public List<PositionElement> getAccountPositions()
    throws RobinhoodApiException {

        //Get the entire watchlist for the account
        List<PositionElement> accountWatchlist = this.getAccountWatchlist();

        //Parse the watchlist for things which have a quantity
        // >= 1 and return it
        Vector<PositionElement> accountPositions = new Vector<>();

        accountWatchlist.forEach( position -> {
            if(position.getQuantity() >= 1) {
                accountPositions.add(position);
            }
        });
        return accountPositions;
    }

    /**
     * @return Closed and open orders.
     * @throws NotLoggedInException
     * @throws RobinhoodApiException
     */
    public List<SecurityOrderElement> getOrders()
    throws RobinhoodApiException {
        SecurityOrderElementList orders;
        //Setup the web method call
        ApiMethod method = new GetOrdersMethod(this.config);
        method.addAuthTokenParameter();
        //Attempt to GET from Robinhood API
        orders = requestManager.makeApiRequest(method);
        //Return the list from the paginated object from the call
        return orders.getResults();
    }

    /**
     * Method which returns a {@link SecurityOrderElement} after running a LIMIT order
     * given the supplied parameters.
     * @param ticker The ticker which the buy or sell order should be performed on
     * @param timeInForce The Enum representation for when this order should be made
     * @param limitPrice The price you're willing to accept in a sell, or pay in a buy
     * @param quantity The number of shares you would like to buy or sell
     * @param orderType Which type of order is being made. A buy, or sell.
     * @throws TickerNotFoundException Thrown when the ticker supplied to the
     *                                  method is invalid.
     * @throws NotLoggedInException  Thrown when this Robinhood Api
     *      instance is not logged into an account. Run the login method first.
     * @throws RobinhoodApiException
     * @return The {@link SecurityOrderElement} that was made
     */
    public SecurityOrderElement makeLimitOrder(String ticker, TimeInForce timeInForce,
                                               float limitPrice, int quantity,
                                               OrderTransactionType orderType)
    throws TickerNotFoundException, RobinhoodApiException {

        //Create the API method
        ApiMethod method = new MakeLimitOrder(ticker, timeInForce, limitPrice,
                quantity, orderType, this.config);
        method.addAuthTokenParameter();
        return requestManager.makeApiRequest(method);

    }

    /**
     * Method which returns a {@link SecurityOrderElement} after running a
     * LIMIT STOP order given the supplied parameters
     * @param ticker The ticker which the buy or sell order should be performed on
     * @param timeInForce The Enum representation for when this order should be made
     * @param limitPrice The price you're willing to accept in a sell, or pay in a buy
     * @param quantity The number of shares you would like to buy or sell
     * @param orderType Which type of order is being made. A buy, or a sell
     * @param stopPrice The price at which the stop trigger converts the order
     *                      into a market order
     * @throws TickerNotFoundException The ticker supplied is not valid.
     * @throws RobinhoodApiException There is a general problem with the API.
     */
    public SecurityOrderElement makeLimitStopOrder(String ticker, TimeInForce timeInForce,
                                                   float limitPrice, int quantity,
                                                   OrderTransactionType orderType,
                                                   float stopPrice)
    throws TickerNotFoundException, RobinhoodApiException {

        //Create the API method
        ApiMethod method = new MakeLimitStopOrder(ticker, timeInForce, limitPrice,
                quantity, orderType, stopPrice, this.config);
        method.addAuthTokenParameter();
        return requestManager.makeApiRequest(method);

    }

    /**
     *
     * @param ticker What ticker you are performing this order on
     * @param quantity How many shares should be transacted
     * @param orderType Which type of order is being made. A buy, or a sell.
     * @param time The Enum representation of when this order should be made.
     * @return The SecurityOrderElement object with the API response.
     * @throws TickerNotFoundException if the ticker supplied was invalid
     * @throws RobinhoodApiException
     */
    public SecurityOrderElement makeMarketOrder(String ticker, int quantity,
                                                OrderTransactionType orderType,
                                                TimeInForce time)
    throws TickerNotFoundException, RobinhoodApiException {

        //Create the API method
        ApiMethod method = new MakeMarketOrder(ticker, quantity, orderType, time,
                this.config);
        method.addAuthTokenParameter();
        return requestManager.makeApiRequest(method);

    }

    /**
     * TODO Docs
     * @param ticker
     * @param quantity
     * @param orderType
     * @param time
     * @param stopPrice
     * @return
     * @throws RobinhoodApiException
     * @throws TickerNotFoundException
     */
    public SecurityOrderElement makeMarketStopOrder(String ticker, int quantity,
                                                    OrderTransactionType orderType,
                                                    TimeInForce time, float stopPrice)
    throws RobinhoodApiException, TickerNotFoundException {
        //Create the API method
        ApiMethod method = new MakeMarketStopOrder(ticker, quantity, orderType, time,
                                                   stopPrice, this.config);
        method.addAuthTokenParameter();
        return requestManager.makeApiRequest(method);
    }

    /**
     * Cancel an order. The order must be open and not completed.
     * @param order The order to cancel
     * @return The cancelled order
     * @throws RobinhoodApiException
     */
    public SecurityOrderElement cancelOrder(SecurityOrderElement order)
    throws RobinhoodApiException {
        ApiMethod method = new CancelOrderMethod(order, this.config);
        method.addAuthTokenParameter();
        return requestManager.makeApiRequest(method);
    }

    /**
     * TODO DOCS
     * @return
     * @throws RobinhoodApiException
     */
    public List<Option> getOptions() throws RobinhoodApiException {
        ApiMethod method = new GetOptionsMethod(this.config);
        method.addAuthTokenParameter();
        OptionElementList optionElementList = requestManager.makeApiRequest(method);
        return optionElementList.getResults();
    }

	//PUBLIC DATA

	/**
	 * Method returning a {@link TickerFundamentalElement} for the supplied ticker name
	 * @param ticker The Stock's ticker
	 * @throws RobinhoodApiException
	 */
	public TickerFundamentalElement getFundamental(String ticker)
    throws RobinhoodApiException {
		//Create the API method
		ApiMethod method = new GetTickerFundamental(ticker);
		return requestManager.makeApiRequest(method);
	}

	/**
	 * Get a {@link List} of {@link TickerFundamentalElement}.
	 * @param tickers A collection of stock tickers
	 * @return a {@link List} of {@link TickerFundamentalElement}.
	 * @throws RobinhoodApiException If an error occurs when making the request
	 * @throws RequestTooLargeException If the Collection is longer than 10
	 * @author Jonathan Augustine
	 */
	public List<TickerFundamentalElement> getFundamentalList(Collection<String> tickers)
	throws RobinhoodApiException {
		TickerFundimentalElementList list = requestManager.makeApiRequest(
				new GetTickerFundamentalList(tickers)
		);
		PaginatedIterator<TickerFundamentalElement> it
				= new PaginatedIterator<>(list);
		List<TickerFundamentalElement> out = new ArrayList<>();
		while (it.hasNext()) { out.add(it.next()); }
		return out;
	}

	/**
	 * Method returning a {@link TickerQuoteElement} for the supplied ticker.
     * Contains general information, such as the current asking price and the
     * last trading price. Does not require the API to be logged on.
	 * @param ticker Which symbol you are retrieving a quote for
	 * @return TickerQuoteElement
     * @throws RobinhoodApiException
     * @throws TickerNotFoundException If the quote is not found
	 */
	public TickerQuoteElement getQuoteByTicker(String ticker)
    throws RobinhoodApiException {
		//Create the API method
		ApiMethod method = new GetTickerQuote(ticker);
        TickerQuoteElement quote = requestManager.makeApiRequest(method);
        if (quote == null) throw new TickerNotFoundException();
		return quote;
	}

    /**
     * Get a list of security quotes by their tickers. The result is
     * SemiPaginated, which is why this can return a normal List
     * @param tickers The tickers to get quotes of (e.g. MSFT, FIT)
     * @return A list of {@link TickerQuoteElement TickerQuoteElements}.
     *          A value in the list may be null if the ticker was not found
     *          on Robinhood.
     * @throws RobinhoodApiException
     * @throws RequestTooLargeException if the collection is longer than 1,630
     */
	public List<TickerQuoteElement> getQuoteListByTickers(Collection<String> tickers)
    throws RobinhoodApiException {
        ApiMethod method = new GetTickerQuoteList(tickers);
        TickerQuoteElementList list = requestManager.makeApiRequest(method);
        return list.getQuotes();
   }

    /**
     * @param ticker The stock ticker
     * @return The {@link InstrumentElement} requested
     *
     * @throws RobinhoodApiException Generic exception from {@link RequestManager}
     * @throws TickerNotFoundException If the ticker is not tracked by Robinhood
     *
     * @author Jonathan Augustine
     */
    public InstrumentElement getInstrumentByTicker(String ticker)
    throws RobinhoodApiException, TickerNotFoundException {
        ApiMethod method = new GetInstrumentByTicker(ticker);
        InstrumentElementList list = requestManager.makeApiRequest(method);
        if (!list.isEmpty()) return list.getResults().get(0);
        throw new TickerNotFoundException(ticker);
    }

    /**
     * Gets a list of instruments by searching with the given keyword.
     * As of July 2018, it seems as this will not return a list greater than 10
     * elements.
     * @param keyword The keyword to search with
     * @return A {@link List} of {@link InstrumentElement InstrumentElements}
     *                  returned by Robinhood's search
     * @throws RobinhoodApiException
     */
    public List<InstrumentElement> getInstrumentsByKeyword(String keyword)
    throws RobinhoodApiException {
        ApiMethod method = new SearchInstrumentsByKeyword(keyword);
        InstrumentElementList list = requestManager.makeApiRequest(method);
        return list.getResults();
    }

    /**
     * Get's every {@link InstrumentElement} tracked by Robinhood.
     * This method performs several calls to the Robinhood servers and is
     * therefore rather expensive to use. Try to use it sparingly (it's not
     * like it's going to be changing all the time)
     * @return Every {@link InstrumentElement} tracked by Robinhood
     * @throws RobinhoodApiException Generic exception from the
     *                                  {@link RequestManager}
     */
    public List<InstrumentElement> getAllInstruments()
    throws RobinhoodApiException {
        ApiMethod method = GetAllInstruments.getDefault();
        InstrumentElementList list = requestManager.makeApiRequest(method);
        ArrayList<InstrumentElement> normalList = new ArrayList<>();
        PaginatedIterator<InstrumentElement> iterator
                = new PaginatedIterator<>(list, config);
        iterator.forEachRemaining(normalList::add);
        return normalList;
    }

	/**
	 * Gets the collection data from Robinhood based on the given Collection
	 * Name. This method does not require a security token.
	 *
	 * Examples of collections include 'manufacturing', 'consumer-product', and
	 * '100-most-popular'
	 *
	 * @param collectionName
	 *            the collection name
	 * @return the collection data as a list of {@link InstrumentElement}.
	 * @throws RobinhoodApiException
	 *             the robinhood api exception
	 *
	 * @author MainStringArgs
	 */
	public InstrumentCollectionList getCollectionData(String collectionName)
	throws RobinhoodApiException {

		// Create the API method
		ApiMethod method = new GetCollectionData(collectionName);

		InstrumentCollectionList response = requestManager.makeApiRequest(method);
		return response;
	}

    /**
     * Build an {@link Iterable} based off a {@link PaginatedIterator}.
     * @param elementList The {@link ApiElementList} build from
     * @param <E> The ApiElement of the List
     * @return a "Paginated" Iterable
     */
    public <E extends ApiElement> Iterable<E> buildIterable(ApiElementList<E> elementList) {
    	return () -> new PaginatedIterator<E>(elementList, RobinhoodApi.this.config);
	}

	/** @return {@code true} if the API has been logged in */
	public boolean isLoggedIn() {
            return this.config.hasToken();
    }

    /** @return The API instance's {@link Configuration} */
	public Configuration getConfig() {
		return config;
	}
}
