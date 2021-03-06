/**
 * This file is part of alf.io.
 *
 * alf.io is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * alf.io is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with alf.io.  If not, see <http://www.gnu.org/licenses/>.
 */
package alfio.repository;

import alfio.datamapper.Bind;
import alfio.datamapper.Query;
import alfio.datamapper.QueryRepository;
import alfio.datamapper.QueryType;
import alfio.model.Ticket;

import java.util.List;

@QueryRepository
public interface TicketRepository {

	@Query(type = QueryType.TEMPLATE, value = "insert into ticket (uuid, creation, category_id, event_id, status, original_price_cts, paid_price_cts)"
			+ "values(:uuid, :creation, :categoryId, :eventId, :status, :originalPrice, :paidPrice)")
	String bulkTicketInitialization();

	@Query("select id from ticket where status = 'FREE' and category_id = :categoryId and event_id = :eventId and tickets_reservation_id is null limit :amount for update")
	List<Integer> selectTicketInCategoryForUpdate(@Bind("eventId") int eventId, @Bind("categoryId") int categoryId,
			@Bind("amount") int amount);

    @Query("select count(*) from ticket where status in ('ACQUIRED', 'CHECKED_IN') and category_id = :categoryId and event_id = :eventId")
    Integer countConfirmedTickets(@Bind("eventId") int eventId, @Bind("categoryId") int categoryId);

    @Query("select * from ticket where status in ('PENDING', 'ACQUIRED', 'CANCELLED', 'CHECKED_IN') and category_id = :categoryId and event_id = :eventId")
    List<Ticket> findAllModifiedTickets(@Bind("eventId") int eventId, @Bind("categoryId") int categoryId);
    
    @Query("select count(*) from ticket where status not in ('ACQUIRED', 'CHECKED_IN')  and category_id = :categoryId and event_id = :eventId")
    Integer countUnsoldTicket(@Bind("eventId") int eventId, @Bind("categoryId") int categoryId);

	@Query("update ticket set tickets_reservation_id = :reservationId, status = 'PENDING' where id in (:reservedForUpdate)")
	int reserveTickets(@Bind("reservationId") String reservationId,
			@Bind("reservedForUpdate") List<Integer> reservedForUpdate);
	
	@Query("update ticket set tickets_reservation_id = :reservationId, special_price_id_fk = :specialCodeId, status = 'PENDING' where id = :ticketId")
	void reserveTicket(@Bind("reservationId")String transactionId, @Bind("ticketId") int ticketId, @Bind("specialCodeId") int specialCodeId);

	@Query("update ticket set status = :status where tickets_reservation_id = :reservationId")
	int updateTicketStatus(@Bind("reservationId") String reservationId, @Bind("status") String status);

    @Query("update ticket set status = 'INVALIDATED' where event_id = :eventId and status in ('FREE', 'CANCELLED')")
    int invalidateAllTickets(@Bind("eventId") int eventId);

	@Query("update ticket set status = 'FREE', tickets_reservation_id = null, special_price_id_fk = null where status = 'PENDING' "
			+ " and tickets_reservation_id in (:reservationIds)")
	int freeFromReservation(@Bind("reservationIds") List<String> reservationIds);

	@Query("select * from ticket where tickets_reservation_id = :reservationId order by category_id asc, uuid asc")
	List<Ticket> findTicketsInReservation(@Bind("reservationId") String reservationId);

    @Query("select count(*) from ticket where tickets_reservation_id = :reservationId")
    Integer countTicketsInReservation(@Bind("reservationId") String reservationId);
	
	@Query("select * from ticket where uuid = :uuid")
	Ticket findByUUID(@Bind("uuid") String uuid);

	@Query("update ticket set email_address = :email, full_name = :fullName where uuid = :ticketIdentifier")
	int updateTicketOwner(@Bind("ticketIdentifier") String ticketIdentifier, @Bind("email") String email, @Bind("fullName") String fullName);
	
	@Query("update ticket set job_title = :jobTitle, company = :company, phone_number = :phoneNumber, address = :address, country = :country, tshirt_size = :tShirtSize where uuid = :ticketIdentifier")
	int updateOptionalTicketInfo(@Bind("ticketIdentifier") String ticketIdentifier, @Bind("jobTitle") String jobTitle, @Bind("company") String company, @Bind("phoneNumber") String phoneNumber, @Bind("address") String address, @Bind("country") String country, @Bind("tShirtSize") String tShirtSize);
}
