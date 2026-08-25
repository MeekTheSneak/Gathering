package dev.gathering.core.collection;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Who may do what with somebody else's collection. */
class CollectionRightsTest {

    private static final UUID OWNER = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID FRIEND = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID STRANGER = UUID.fromString("33333333-3333-4333-8333-333333333333");

    @Test
    @DisplayName("a fresh collection belongs to whoever put it down and to nobody else")
    void ownerOnlyToBeginWith() {
        CollectionRights rights = CollectionRights.ownedBy(OWNER);

        assertThat(rights.mayTake(OWNER)).isTrue();
        assertThat(rights.mayAdd(OWNER)).isTrue();
        assertThat(rights.mayTake(FRIEND)).isFalse();
        assertThat(rights.mayAdd(FRIEND)).isFalse();
        assertThat(rights.isShared()).isFalse();
    }

    @Test
    @DisplayName("anybody may look, always")
    void lookingIsNotARight() {
        // A collection is a thing you show off, and browsing the playgroup's pool without
        // asking is most of what one is for.
        CollectionRights rights = CollectionRights.ownedBy(OWNER);

        assertThat(rights.mayLook(STRANGER)).isTrue();
        assertThat(CollectionRights.NOBODYS.mayLook(STRANGER)).isTrue();
    }

    @Test
    @DisplayName("taking and adding are separate, which is what buys a donation box")
    void theTwoRightsAreSeparate() {
        CollectionRights donationBox = CollectionRights.ownedBy(OWNER)
                .allowingAdd(FRIEND)
                .allowingAdd(STRANGER);

        assertThat(donationBox.mayAdd(STRANGER)).isTrue();
        assertThat(donationBox.mayTake(STRANGER)).isFalse();

        CollectionRights lendingLibrary = CollectionRights.ownedBy(OWNER).allowingTake(FRIEND);

        assertThat(lendingLibrary.mayTake(FRIEND)).isTrue();
        assertThat(lendingLibrary.mayAdd(FRIEND)).isFalse();
    }

    @Test
    @DisplayName("a right granted can be taken away again")
    void rightsCanBeRevoked() {
        CollectionRights rights = CollectionRights.ownedBy(OWNER)
                .allowingTake(FRIEND)
                .allowingAdd(FRIEND);

        assertThat(rights.refusingTake(FRIEND).mayTake(FRIEND)).isFalse();
        assertThat(rights.refusingTake(FRIEND).mayAdd(FRIEND))
                .as("taking one right away does not take the other")
                .isTrue();
        assertThat(rights.refusingAdd(FRIEND).refusingTake(FRIEND).isShared()).isFalse();
    }

    @Test
    @DisplayName("the owner's rights are not a list entry, so they cannot be revoked")
    void theOwnerCannotBeLockedOut() {
        CollectionRights rights = CollectionRights.ownedBy(OWNER).refusingTake(OWNER);

        assertThat(rights.mayTake(OWNER)).isTrue();
        assertThat(rights.isShared()).isFalse();
    }

    @Test
    @DisplayName("handing a collection over takes the new owner off both lists")
    void handingItOverTidiesUp() {
        // Otherwise an owner who was also listed would keep the right after being taken off
        // the list, which is the kind of thing nobody notices until it matters.
        CollectionRights rights = CollectionRights.ownedBy(OWNER)
                .allowingTake(FRIEND)
                .allowingAdd(FRIEND);

        CollectionRights handedOver = rights.ownedNowBy(FRIEND);

        assertThat(handedOver.owner()).isEqualTo(FRIEND);
        assertThat(handedOver.mayTake()).doesNotContain(FRIEND);
        assertThat(handedOver.mayAdd()).doesNotContain(FRIEND);
        assertThat(handedOver.mayTake(FRIEND)).isTrue();
        assertThat(handedOver.mayTake(OWNER)).isFalse();
    }

    @Test
    @DisplayName("a collection nobody owns lets nobody touch it")
    void anUnownedCollectionIsNobodys() {
        assertThat(CollectionRights.NOBODYS.mayTake(OWNER)).isFalse();
        assertThat(CollectionRights.NOBODYS.mayAdd(OWNER)).isFalse();
        assertThat(CollectionRights.NOBODYS.isOwner(null)).isFalse();
    }

    @Test
    @DisplayName("nobody is nobody, however they are asked about")
    void nullIsNotAPlayer() {
        CollectionRights rights = CollectionRights.ownedBy(OWNER).allowingTake(FRIEND);

        assertThat(rights.mayTake(null)).isFalse();
        assertThat(rights.mayAdd(null)).isFalse();
        assertThat(rights.allowingTake(null)).isEqualTo(rights);
    }

    @Test
    @DisplayName("who was let in stays in the order they were let in")
    void theListDoesNotWander() {
        CollectionRights rights = CollectionRights.ownedBy(OWNER)
                .allowingTake(STRANGER)
                .allowingTake(FRIEND);

        assertThat(rights.mayTake()).containsExactly(STRANGER, FRIEND);
    }

    @Test
    @DisplayName("a list handed out cannot be edited behind its back")
    void theListsAreNotWritable() {
        CollectionRights rights = new CollectionRights(OWNER, Set.of(FRIEND), Set.of());

        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> rights.mayTake().add(STRANGER))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
